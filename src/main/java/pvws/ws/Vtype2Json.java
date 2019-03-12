/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import static pvws.PVWebSocketContext.json_factory;

import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;

import org.epics.vtype.AlarmSeverity;
import org.epics.vtype.VDouble;
import org.epics.vtype.VEnum;
import org.epics.vtype.VFloat;
import org.epics.vtype.VNumber;
import org.epics.vtype.VString;
import org.epics.vtype.VType;

import com.fasterxml.jackson.core.JsonGenerator;

public class Vtype2Json
{
    public static String toJson(final String name, final VType value, final VType last_value) throws Exception
    {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        g.writeStartObject();
        g.writeStringField("type", "update");
        g.writeStringField("pv", name);

        if (value instanceof VNumber)
            toJson(g, (VNumber) value, last_value);
        else if (value instanceof VString)
            toJson(g, (VString) value);
        else if (value instanceof VEnum)
            toJson(g, (VEnum) value, last_value);
        else
        {
            // TODO Many more types
            g.writeStringField("value", value.toString());
        }

        g.writeEndObject();
        g.flush();
        return buf.toString();
    }

    private static void toJson(final JsonGenerator g,  VString value) throws Exception
    {
        g.writeStringField("severity", value.getAlarm().getSeverity().name());
        g.writeStringField("text", value.getValue());
    }

    private static void toJson(final JsonGenerator g, final VNumber value, final VType last_value) throws Exception
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

        if (value instanceof VDouble  ||  value instanceof VFloat)
        {
            final double dbl = value.getValue().doubleValue();
            if (Double.isFinite(dbl))
                g.writeNumberField("value", dbl);
            else
                g.writeStringField("text", "NaN");
        }
        else
            g.writeNumberField("value", value.getValue().longValue());
    }

    private static void toJson(final JsonGenerator g, final VEnum value, final VType last_value) throws Exception
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
