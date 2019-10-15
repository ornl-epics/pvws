/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import java.io.IOException;
import java.util.Map;

import javax.servlet.annotation.WebServlet;

import org.phoebus.util.time.TimestampFormats;

import com.fasterxml.jackson.core.JsonGenerator;

import pvws.PVWebSocketContext;

/** Servlet to provide server info
 *  @author Kay Kasemir
 */
@WebServlet("/info")
public class InfoServlet extends JSONServlet
{
	private static final long serialVersionUID = 1L;

    @Override
    protected void writeJson(JsonGenerator g) throws IOException
    {
        g.writeStartObject();
        g.writeStringField("start_time", TimestampFormats.SECONDS_FORMAT.format(PVWebSocketContext.start_time));
        g.writeStringField("jre", System.getProperty("java.vendor") + " " + System.getProperty("java.version"));

        g.writeArrayFieldStart("env");
        for (final Map.Entry<String, String> entry : System.getenv().entrySet())
        {
            g.writeStartObject();
            g.writeStringField(entry.getKey(), entry.getValue());
            g.writeEndObject();
        }
        g.writeEndArray();

        g.writeEndObject();
    }
}
