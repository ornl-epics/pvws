/*******************************************************************************
 * Copyright (c) 2019 UT-Battelle, LLC.
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
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Base64;

import org.epics.util.array.ListByte;
import org.epics.util.array.ListNumber;
import org.epics.util.stats.Range;
import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.VByteArray;
import org.epics.vtype.VDouble;
import org.epics.vtype.VDoubleArray;
import org.epics.vtype.VEnum;
import org.epics.vtype.VFloat;
import org.epics.vtype.VFloatArray;
import org.epics.vtype.VNumber;
import org.epics.vtype.VNumberArray;
import org.epics.vtype.VString;
import org.epics.vtype.VType;

import com.fasterxml.jackson.core.JsonGenerator;

public class Vtype2Json
{
    private static final Charset UTF8 = Charset.forName("UTF-8");


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
            handleLongString(g, (VByteArray) value);
        
        // Serialize double and float arrays as b64dbl
        else if (value instanceof VDoubleArray)
            handleDoubles(g, (VNumberArray) value, last_value);
        else if (value instanceof VFloatArray)
            handleDoubles(g, (VNumberArray) value, last_value);
        
        // Serialize remaining number arrays (int, short) as b64int
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

        g.writeEndObject();
        g.flush();
        return buf.toString();
    }


    private static void handleString(final JsonGenerator g, final VString value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null  ||
            (last_value instanceof VString  &&
             ((VString) last_value).getAlarm().getSeverity() != severity))
            g.writeStringField("severity", value.getAlarm().getSeverity().name());

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


    private static void handleNumber(final JsonGenerator g, final VNumber value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("units", value.getDisplay().getUnit());

            final NumberFormat format =  value.getDisplay().getFormat();
            if (format instanceof DecimalFormat)
                g.writeNumberField("precision", ((DecimalFormat) format).getMaximumFractionDigits());

            g.writeStringField("severity", severity.name());

            final Range range = value.getDisplay().getDisplayRange();
            g.writeNumberField("min", range.getMinimum());
            g.writeNumberField("max", range.getMaximum());
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
            g.writeStringField("units", value.getDisplay().getUnit());

            final NumberFormat format =  value.getDisplay().getFormat();
            if (format instanceof DecimalFormat)
                g.writeNumberField("precision", ((DecimalFormat) format).getMaximumFractionDigits());

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


    private static void handleInts(final JsonGenerator g, final VNumberArray value, final VType last_value) throws Exception
    {
        final AlarmSeverity severity = value.getAlarm().getSeverity();
        if (last_value == null)
        {
            // Initially, add complete metadata
            g.writeStringField("units", value.getDisplay().getUnit());

            final NumberFormat format =  value.getDisplay().getFormat();
            if (format instanceof DecimalFormat)
                g.writeNumberField("precision", ((DecimalFormat) format).getMaximumFractionDigits());

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
            g.writeArrayFieldStart("labels");
            for (final String label : value.getDisplay().getChoices())
                g.writeString(label);
            g.writeEndArray();

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
