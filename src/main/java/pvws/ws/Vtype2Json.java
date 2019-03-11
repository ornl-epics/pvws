/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import static pvws.PVWebSocketContext.json_factory;

import java.io.ByteArrayOutputStream;

import org.epics.vtype.VType;

import com.fasterxml.jackson.core.JsonGenerator;

public class Vtype2Json
{
    public static String toJson(final String name, final VType value, final boolean complete) throws Exception
    {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        g.writeStartObject();
        g.writeStringField("type", "update");
        g.writeStringField("pv", name);
        g.writeStringField("value", value.toString());
        g.writeEndObject();
        g.flush();
        return buf.toString();

    }
}
