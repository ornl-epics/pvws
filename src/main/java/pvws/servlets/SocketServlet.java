/*******************************************************************************
 * Copyright (c) 2019-2020 UT-Battelle, LLC.
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

import org.epics.vtype.Array;
import org.epics.vtype.VType;

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

	/** GET /socket : Return info about all sockets and their PVs
	 *
	 *  <p>Use /socket/{id} to only get into about selected socket ID.
	 */
	@Override
    protected void writeJson(final HttpServletRequest request, final JsonGenerator g) throws IOException
	{
	    final String selected_id = request.getPathInfo() == null
	                             ? null
	                             : request.getPathInfo().substring(1);
        g.writeStartObject();
        g.writeArrayFieldStart("sockets");
        for (final WebSocket socket : PVWebSocketContext.getSockets())
        {
            if (selected_id != null  &&  !socket.getId().equals(selected_id))
                continue;
            g.writeStartObject();
            g.writeStringField("id", socket.getId());
            g.writeNumberField("created", socket.getCreateTime());
            g.writeNumberField("last_client_message", socket.getLastClientMessage());
            g.writeNumberField("last_message_sent", socket.getLastMessageSent());
            g.writeNumberField("queued", socket.getQueuedMessageCount());

            g.writeArrayFieldStart("pvs");
            for (final WebSocketPV pv : socket.getPVs())
            {
                g.writeStartObject();
                g.writeStringField("name", pv.getName());
                // Add representation of value.
                final VType value = pv.getLastValue();
                if (value instanceof Array)
                {   // For arrays, show size, not actual elements
                    g.writeStringField("value", value.getClass().getName() + ", size " + ((Array) value).getSizes());
                }
                else
                    g.writeStringField("value", Objects.toString(pv.getLastValue()));
                g.writeEndObject();
            }
            g.writeEndArray();

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
