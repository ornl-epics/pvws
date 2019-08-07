/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import static pvws.PVWebSocketContext.logger;

import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonGenerator;

import pvws.PVWebSocketContext;
import pvws.ws.WebSocket;
import pvws.ws.WebSocketPV;

/** Servlet to list Web Sockets and their PVs
 *  @author Kay Kasemir
 */
@WebServlet("/socket/*")
public class SocketServlet extends JSONServlet
{
	private static final long serialVersionUID = 1L;

	/** GET /socket : Return info about all sockets and their PVs */
	@Override
    protected void writeJson(final JsonGenerator g) throws IOException
	{
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
                g.writeStringField("name", pv.getName());
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
	}

    /** DELETE /socket/ID : Close socket and its PVs */
	@Override
    protected void doDelete(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
	{
	    final String id = request.getPathInfo().substring(1);
	    for (final WebSocket socket : PVWebSocketContext.getSockets())
            if (id.equals(socket.getId()))
            {
                logger.log(Level.INFO, "DELETE socket '" + id + "'");
                socket.dispose();
                return;
            }
        logger.log(Level.WARNING, "Cannot DELETE socket '" + id + "'");
	}
}
