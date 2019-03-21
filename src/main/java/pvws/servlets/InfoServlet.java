/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import static pvws.PVWebSocketContext.json_factory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonGenerator;

import pvws.PVWebSocketContext;
import pvws.ws.WebSocket;
import pvws.ws.WebSocketPV;

/** Servlet to list Web Sockets and their PVs
 *  @author Kay Kasemir
 */
@WebServlet("/info")
public class InfoServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;

	@Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
	{
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        g.writeStartObject();
        g.writeArrayFieldStart("sockets");
        for (final WebSocket socket : PVWebSocketContext.getSockets())
        {
            g.writeStartObject();
            g.writeStringField("id", socket.getId());

            g.writeArrayFieldStart("pvs");
            for (final WebSocketPV pv : socket.getPVs())
            {
                g.writeStartObject();
                g.writeStringField("pv", pv.getName());
                g.writeStringField("value", Objects.toString(pv.getLastValue()));
                g.writeEndObject();
            }
            g.writeEndArray();

            g.writeNumberField("queued", socket.getQueuedMessageCount());
            g.writeNumberField("last_client_message", socket.getLastClientMessage());

            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
        g.flush();

        response.setContentType("application/json");
        final PrintWriter writer = response.getWriter();
        writer.append(buf.toString());

	}

	@Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		// TODO Auto-generated method stub
	}

}
