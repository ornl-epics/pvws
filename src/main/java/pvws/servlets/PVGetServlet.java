package pvws.servlets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.epics.util.array.ListNumber;
import org.epics.vtype.Array;
import org.epics.vtype.Scalar;
import org.epics.vtype.Time;
import org.epics.vtype.VByteArray;
import org.epics.vtype.VDouble;
import org.epics.vtype.VDoubleArray;
import org.epics.vtype.VEnum;
import org.epics.vtype.VFloat;
import org.epics.vtype.VFloatArray;
import org.epics.vtype.VInt;
import org.epics.vtype.VIntArray;
import org.epics.vtype.VNumber;
import org.epics.vtype.VNumberArray;
import org.epics.vtype.VShortArray;
import org.epics.vtype.VString;
import org.epics.vtype.VStringArray;
import org.epics.vtype.VType;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pvws.PVWebSocketContext;
import pvws.ws.WebSocket;
import pvws.ws.WebSocketPV;
import static pvws.PVWebSocketContext.json_factory;


/**
 * Servlet to handle requests for fetching PV values.
 * This servlet supports both single and multiple PV requests via the "pv" query parameter.
 */
@WebServlet("/pvget")
public class PVGetServlet extends JSONServlet {
    private static final long serialVersionUID = 1L;

    /**
     * Handles GET requests to /pvget and writes the response in JSON format.
     * 
     * The method supports two input formats:
     * - A single PV name passed directly via the "pv" parameter.
     * - Multiple PV names passed as a JSON array of strings via the "pv" parameter.
     * 
     * @param request The HTTP request containing the "pv" parameter specifying one or more PVs.
     * @param g       The JsonGenerator used to write the JSON response.
     * @throws IOException If an I/O error occurs while writing the JSON response.
     */
    @Override
    protected void writeJson(final HttpServletRequest request, final JsonGenerator g) throws IOException
    {
        // Retrieve the "pv" parameter from the request, which specifies the PV(s) to fetch.
        String pvParam = request.getParameter("pv");

        // If no PVs are specified, return an error response.
        if (pvParam == null || pvParam.isEmpty()) {
            g.writeStartObject();
            g.writeStringField("name", pvParam);
            g.writeBooleanField("success", false);
            g.writeStringField("message", "No PVs have been specified.");
            g.writeEndObject();
            return;
        }

        List<String> pvs = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper(json_factory);

        try {
            // Try parsing as JSON array
            JsonNode node = mapper.readTree(pvParam);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    pvs.add(item.asText());
                }
            } else {
                // Not an array, assume it's a single PV name
                pvs.add(pvParam);
            }
        } catch (JsonProcessingException e) {
            // Invalid JSON, treat it as a single PV name
            pvs.add(pvParam);
        }
        
        // Handle single PV request.
        if (pvs.size() == 1) {
            try {
                // Generate a JSON object for the single PV and write it directly to the response.
                String jsonObject = generateJsonForPV(pvs.get(0));
                g.writeRawValue(jsonObject); // Write raw JSON string
            } catch (Exception e) {
                // If an exception occurs, return an error response for the single PV.
                g.writeStartObject();
                g.writeStringField("name", pvs.get(0));
                g.writeBooleanField("success", false);
                g.writeStringField("message", "An exception occurs when fetching or formatting PV value.");
                g.writeEndObject();
            }
        } else {
            // Handle multiple PV requests using a thread pool for concurrent processing.
            int numberOfThreads = 100; // Set a large number of threads for I/O-bound tasks.
            ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

            // Create a list to store tasks for generating JSON for each PV.
            List<PVTask> pvTasks = new ArrayList<>();

            // Submit tasks to the thread pool to generate JSON for each PV concurrently.
            for (String pv : pvs) {
                Future<String> future = executorService.submit(() -> generateJsonForPV(pv));
                pvTasks.add(new PVTask(pv, future));
            }

            g.writeStartArray();
            for (PVTask pvTask : pvTasks) {
                try {
                    // Retrieve the JSON result for the PV from the Future and write it to the response.
                    String jsonObject = pvTask.future.get(5000, TimeUnit.MILLISECONDS);
                    g.writeRawValue(jsonObject); // Write raw JSON string
                } catch (TimeoutException e) {
                    // Handle case where generating JSON for this PV timed out.
                    g.writeStartObject();
                    g.writeStringField("name", pvTask.pvName);
                    g.writeBooleanField("success", false);
                    g.writeStringField("message", "Timeout while generating JSON for the PV.");
                    g.writeEndObject();
                } catch (InterruptedException | ExecutionException e) {
                    // Handle exceptions thrown during task execution.
                    g.writeStartObject();
                    g.writeStringField("name", pvTask.pvName);
                    g.writeBooleanField("success", false);
                    g.writeStringField("message", "An exception occurs when generating JSON for the PV.");
                    g.writeEndObject();
                }
            }
            g.writeEndArray();

            // Shutdown the thread pool to release resources.
            executorService.shutdown();
        }
    }


    /**
     * Generates a JSON representation of the PV with the given name.
     *
     * @param name The name of the PV to generate the JSON for.
     * 
     * @return A JSON-formatted string representing the PV's metadata and value.
     *         The JSON includes information such as:
     *         - Success status and error messages (if applicable).
     *         - PV type, value type, and other metadata.
     *         - Timestamps (seconds and nanoseconds) if available.
     *         - Alarm information (severity, status, name) if applicable.
     *         - The actual value of the PV, which can be a scalar, array, or enum.
     * 
     * @throws Exception If there is an issue retrieving the PV, reading its value, or generating the JSON.
     */
    private String generateJsonForPV(String name) throws Exception {
        // Retrieve the PV object from the PVPool using the provided name
        PV pv = PVPool.getPV(name);

        // Attempt to asynchronously read the value of the PV
        VType value = asyncRead(pv, 100, 50);

        // Create a ByteArrayOutputStream to hold the JSON output
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);

        // Start the JSON object
        g.writeStartObject();
        g.writeStringField("name", name);

        // Handle cases where the PV value is null or disconnected
        if (value == null) {
            g.writeBooleanField("success", false);
            g.writeStringField("message", "PV is not found");
        } else if (PV.isDisconnected(value)) {
            g.writeBooleanField("success", false);
            g.writeStringField("message", "PV is disconnected");
        } else {
            // Successfully read the PV value
            g.writeBooleanField("success", true);
            g.writeStringField("message", "PV is read successfully");

            // Write metadata about the PV and its value
            g.writeStringField("pv_type", pv.getClass().getSimpleName());
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            g.writeBooleanField("scalar", value instanceof Scalar);
            g.writeBooleanField("array", value instanceof Array);
            g.writeNumberField("size", value instanceof Array ? ((Array) value).getSizes().getInt(0) : 1);
            g.writeBooleanField("readonly", pv.isReadonly());

            // Include timestamp information if available
            final Time time = Time.timeOf(value);
            if (time != null) {
                g.writeNumberField("seconds", time.getTimestamp().getEpochSecond());
                g.writeNumberField("nanos", time.getTimestamp().getNano());
            }

            // Handle alarm information based on the type of value
            if (value instanceof Scalar) {
                g.writeStringField("alarm", ((Scalar) value).getAlarm().toString());
                g.writeStringField("alarm_severity", ((Scalar) value).getAlarm().getSeverity().toString());
                g.writeStringField("alarm_status", ((Scalar) value).getAlarm().getStatus().toString());
                g.writeStringField("alarm_name", ((Scalar) value).getAlarm().getName());
            } else if (value instanceof VNumberArray) {
                g.writeStringField("alarm", ((VNumberArray) value).getAlarm().toString());
                g.writeStringField("alarm_severity", ((VNumberArray) value).getAlarm().getSeverity().toString());
                g.writeStringField("alarm_status", ((VNumberArray) value).getAlarm().getStatus().toString());
                g.writeStringField("alarm_name", ((VNumberArray) value).getAlarm().getName());
            } else if (value instanceof VStringArray) {
                g.writeStringField("alarm", ((VStringArray) value).getAlarm().toString());
                g.writeStringField("alarm_severity", ((VStringArray) value).getAlarm().getSeverity().toString());
                g.writeStringField("alarm_status", ((VStringArray) value).getAlarm().getStatus().toString());
                g.writeStringField("alarm_name", ((VStringArray) value).getAlarm().getName());
            }

            // Handle the actual value of the PV based on its type
            if (value instanceof VInt) {
                g.writeNumberField("value", ((VNumber) value).getValue().intValue());
            } else if (value instanceof VFloat) {
                g.writeNumberField("value", ((VNumber) value).getValue().floatValue());
            } else if (value instanceof VDouble) {
                g.writeNumberField("value", ((VNumber) value).getValue().doubleValue());
            } else if (value instanceof VString) {
                g.writeStringField("text", ((VString) value).getValue());
            } else if (value instanceof VEnum) {
                g.writeArrayFieldStart("labels");
                for (final String label : ((VEnum) value).getDisplay().getChoices())
                    g.writeString(label);
                g.writeEndArray();
                g.writeNumberField("value", ((VEnum) value).getIndex());
                g.writeStringField("text", ((VEnum) value).getValue());
            } else if (value instanceof VByteArray) {
                final ListNumber data = ((VNumberArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeNumber(data.getByte(i));
                g.writeEndArray();
            } else if (value instanceof VShortArray) {
                final ListNumber data = ((VNumberArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeNumber(data.getShort(i));
                g.writeEndArray();
            } else if (value instanceof VIntArray) {
                final ListNumber data = ((VNumberArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeNumber(data.getInt(i));
                g.writeEndArray();
            } else if (value instanceof VFloatArray) {
                final ListNumber data = ((VNumberArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeNumber(data.getFloat(i));
                g.writeEndArray();
            } else if (value instanceof VDoubleArray) {
                final ListNumber data = ((VNumberArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeNumber(data.getDouble(i));
                g.writeEndArray();
            } else if (value instanceof VStringArray) {
                final List<String> data = ((VStringArray) value).getData();
                final int N = data.size();
                g.writeArrayFieldStart("value");
                for (int i = 0; i < N; ++i)
                    g.writeString(data.get(i));
                g.writeEndArray();
            } else {
                g.writeStringField("text", value.toString());
            }
        }

        // End the JSON object and flush the generator
        g.writeEndObject();
        g.flush();

        // Fetch all PV names from WebSocket connections
        Set<String> activePVs = new HashSet<>();
        for (final WebSocket socket : PVWebSocketContext.getSockets()) {
            for (final WebSocketPV pvItem : socket.getPVs()) {
                activePVs.add(pvItem.getName());
            }
        }

        // Check if the requested PV exists in the active PV list
        if (!activePVs.contains(name)) {
            // Release the PV if it does not exist in the active PV list
            if (pv != null) {
                PVPool.releasePV(pv);
            }
        }

        // Return the JSON string
        return buf.toString();
    }


    /**
     * Asynchronously reads a value from the given PV with retry logic.
     *
     * @param pv           The PV object to read from.
     * @param maxRetries   The maximum number of retry attempts allowed.
     * @param retryDelayMs The delay (in milliseconds) between consecutive retry attempts.
     *                     
     * @return The value read from the PV, or null if all retry attempts fail.
     * @throws InterruptedException If the thread is interrupted during the sleep
     *                              period between retries.
     */
    private VType asyncRead(PV pv, int maxRetries, int retryDelayMs) throws InterruptedException {
        VType value = null;

        // Attempt to read the value from the PV, retrying up to maxRetries times
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                // Attempt to asynchronously read the value from the PV with a timeout of 5000ms
                value = pv.asyncRead().get(5000, TimeUnit.MILLISECONDS);
                // The value may be null for simulated PVs
                if (value != null)
                    break; // Exit the loop if the read is successful and value is not null
            } catch (Exception e) {
                value = null; // Reset value on failure
            }
            Thread.sleep(retryDelayMs); // Wait for the specified delay before retrying
        }

        // Return the value read from the PV, or null if all retries failed
        return value;
    }


    // Helper class to associate a PV name with its Future
    private static class PVTask {
        String pvName;
        Future<String> future;

        PVTask(String pvName, Future<String> future) {
            this.pvName = pvName;
            this.future = future;
        }
    }
}
