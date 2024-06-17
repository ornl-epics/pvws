/*******************************************************************************
 * Copyright (c) 2019-2022 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import static pvws.PVWebSocketContext.json_factory;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Base64;

import org.epics.util.array.ListByte;
import org.epics.util.array.ListNumber;
import org.epics.util.stats.Range;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.Display;
import org.epics.vtype.Time;
import org.epics.vtype.VByteArray;
import org.epics.vtype.VDouble;
import org.epics.vtype.VDoubleArray;
import org.epics.vtype.VEnum;
import org.epics.vtype.VFloat;
import org.epics.vtype.VFloatArray;
import org.epics.vtype.VNumber;
import org.epics.vtype.VNumberArray;
import org.epics.vtype.VShortArray;
import org.epics.vtype.VString;
import org.epics.vtype.VType;

import com.fasterxml.jackson.core.JsonGenerator;

/** Map {@link VType} to JSON
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class Vtype2Json
{
    private static final Charset UTF8 = Charset.forName("UTF-8");

    /** @param name PV Name
     *  @param value Most recent value
     *  @param last_value Previous value or <code>null</code>, used to detect changes
     *  @param last_readonly Was PV read-only?
     *  @param readonly Is PV read-only right now?
     *  @return JSON text
     *  @throws Exception on error
     */
    public static String toJson(final String name, final VType value, final VType last_value, final boolean last_readonly, final boolean readonly) throws Exception
    {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        g.writeStartObject();
        g.writeStringField("type", "update");
        g.writeStringField("pv", name);

        if (value instanceof VNumber)
            handleNumber(g, (VNumber) value, last_value);
        else if (value instanceof VString)
            handleString(g, (VString) value, last_value);
        else if (value instanceof VEnum)
            handleEnum(g, (VEnum) value, last_value);
        else if (value instanceof VByteArray)
            handleBytes(g, (VNumberArray) value, last_value);

        // Serialize double and float arrays as b64dbl
        else if (value instanceof VDoubleArray)
            handleDoubles(g, (VNumberArray) value, last_value);
        else if (value instanceof VFloatArray)
            handleFloats(g, (VNumberArray) value, last_value);
        else if (value instanceof VShortArray)
        	handleShorts(g, (VNumberArray) value, last_value);

        // Serialize remaining number arrays (int) as b64int
        else if (value instanceof VNumberArray)
            handleInts(g, (VNumberArray) value, last_value);

        else if (value != null)
        {
            // TODO Are there more types that need to be handled?
        	// For now pass as text
            g.writeStringField("text", value.toString());
        }
        // null: Neither 'value' nor 'text'

        // Change in read/write access?
        if (last_readonly != readonly)
            g.writeBooleanField("readonly", readonly);

        final Time time = Time.timeOf(value);
        if (time != null)
        {
            g.writeNumberField("seconds", time.getTimestamp().getEpochSecond());
            g.writeNumberField("nanos", time.getTimestamp().getNano());
        }

        g.writeEndObject();
        g.flush();
        return buf.toString();
    }


    private static void handleString(final JsonGenerator g, final VString value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VString) &&
                 ((VString) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        g.writeStringField("text", value.getValue());
    }


    private static void handleLongString(final JsonGenerator g, final VByteArray value) throws Exception
    {
        g.writeStringField("severity", value.getAlarm().getSeverity().name());

        final ListByte data = value.getData();
        final byte[] bytes = new byte[data.size()];
        // Copy bytes until end or '\0'
        int len = 0;
        while (len<bytes.length)
        {
            final byte b = data.getByte(len);
            if (b == 0)
                break;
            else
                bytes[len++] = b;
        }
        // Use actual 'len', not data.size()
        g.writeStringField("text", new String(bytes, 0, len, UTF8));
    }

    private static void handleDisplay(final JsonGenerator g, final Display display) throws Exception
    {
        if (display == null)
            return;

        g.writeStringField("units", display.getUnit());
        g.writeStringField("description", display.getDescription());

        final NumberFormat format =  display.getFormat();
        if (format instanceof DecimalFormat)
            g.writeNumberField("precision", ((DecimalFormat) format).getMaximumFractionDigits());

        Range range = display.getDisplayRange();
        if (range != null)
        {
            g.writeNumberField("min", range.getMinimum());
            g.writeNumberField("max", range.getMaximum());
        }

        range = display.getWarningRange();
        if (range != null)
        {
            g.writeNumberField("warn_low", range.getMinimum());
            g.writeNumberField("warn_high", range.getMaximum());
        }
        range = display.getAlarmRange();
        if (range != null)
        {
            g.writeNumberField("alarm_low", range.getMinimum());
            g.writeNumberField("alarm_high", range.getMaximum());
        }
    }

    private static void handleNumber(final JsonGenerator g, final VNumber value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        if (value instanceof VDouble  ||  value instanceof VFloat)
        {
            final double dbl = value.getValue().doubleValue();
            if (Double.isFinite(dbl))
                g.writeNumberField("value", dbl);
            else
                g.writeStringField("value", "NaN");
        }
        else
            g.writeNumberField("value", value.getValue().longValue());
    }


    private static void handleDoubles(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        // Convert into Base64 double array
        // System.out.println("Encode: " + value.getData());
        final ListNumber data = value.getData();
        final int N = data.size();
        final ByteBuffer buf = ByteBuffer.allocate(N * Double.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final DoubleBuffer dblbuf = buf.asDoubleBuffer();
        for (int i=0; i<N; ++i)
            dblbuf.put(data.getDouble(i));
        g.writeStringField("b64dbl", Base64.getEncoder().encodeToString(buf.array()));
    }


    private static void handleFloats(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        final ListNumber data = value.getData();
        final int N = data.size();
        final ByteBuffer buf = ByteBuffer.allocate(N * Float.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final FloatBuffer fltbuf = buf.asFloatBuffer();
        for (int i=0; i<N; ++i)
            fltbuf.put(data.getFloat(i));
        g.writeStringField("b64flt", Base64.getEncoder().encodeToString(buf.array()));
    }


    private static void handleShorts(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        final ListNumber data = value.getData();
        final int N = data.size();
        final ByteBuffer buf = ByteBuffer.allocate(N * Short.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final ShortBuffer srtbuf = buf.asShortBuffer();
        for (int i=0; i<N; ++i)
        	srtbuf.put(data.getShort(i));
        g.writeStringField("b64srt", Base64.getEncoder().encodeToString(buf.array()));
    }


    private static void handleBytes(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        // Convert into Base64 int64 array
        final ListNumber data = value.getData();
        final int N = data.size();
        final ByteBuffer buf = ByteBuffer.allocate(N * Byte.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i=0; i<N; ++i)
        	buf.put(data.getByte(i));
        g.writeStringField("b64byt", Base64.getEncoder().encodeToString(buf.array()));
    }


    private static void handleInts(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            handleDisplay(g, value.getDisplay());
            // Initial severity
            g.writeStringField("severity", severity.name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        // Convert into Base64 int64 array
        // System.out.println("Encode: " + value.getData());
        final ListNumber data = value.getData();
        final int N = data.size();
        final ByteBuffer buf = ByteBuffer.allocate(N * Integer.BYTES);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        final IntBuffer dblbuf = buf.asIntBuffer();
        for (int i=0; i<N; ++i)
            dblbuf.put(data.getInt(i));
        g.writeStringField("b64int", Base64.getEncoder().encodeToString(buf.array()));
    }


    private static void handleEnum(final JsonGenerator g, final VEnum value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("vtype", VType.typeOf(value).getSimpleName());
            g.writeArrayFieldStart("labels");
            for (final String label : value.getDisplay().getChoices())
                g.writeString(label);
            g.writeEndArray();

            // Initial severity
            g.writeStringField("severity", value.getAlarm().getSeverity().name());
        }
        else
        {
            // Add severity if it changed
            if ((last_value instanceof VNumber)  &&
                ((VNumber) last_value).getAlarm().getSeverity() != severity)
                g.writeStringField("severity", severity.name());
        }

        g.writeNumberField("value",  value.getIndex());
        g.writeStringField("text",  value.getValue());
    }
}
