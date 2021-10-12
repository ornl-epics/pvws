/*******************************************************************************
 * Copyright (c) 2020 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.epics.util.array.ListInteger;
import org.epics.vtype.Array;
import org.epics.vtype.VType;

import com.fasterxml.jackson.core.JsonGenerator;

import pvws.PVWebSocketContext;
import pvws.ws.WebSocket;
import pvws.ws.WebSocketPV;

/** Servlet to list Web Sockets and summarize their PVs
 *  @author Kay Kasemir
 */
@WebServlet("/summary/*")
public class SummaryServlet extends JSONServlet
{
	private static final long serialVersionUID = 1L;

	/** GET /summary : Return info about all sockets and their PVs */
	@Override
    protected void writeJson(final HttpServletRequest request, final JsonGenerator g) throws IOException
	{
        g.writeStartObject();
        g.writeArrayFieldStart("sockets");
        for (final WebSocket socket : PVWebSocketContext.getSockets())
        {
            g.writeStartObject();

            g.writeStringField("id", socket.getId());
            g.writeNumberField("created", socket.getCreateTime());
            g.writeNumberField("last_client_message", socket.getLastClientMessage());
            g.writeNumberField("last_message_sent", socket.getLastMessageSent());
            g.writeNumberField("queued", socket.getQueuedMessageCount());

            int pvs = 0, arrays = 0, max_size = 0;
            for (final WebSocketPV pv : socket.getPVs())
            {
                ++pvs;
                final VType value = pv.getLastValue();
                if (value instanceof Array)
                {
                    ++arrays;
                    final ListInteger sizes = ((Array) value).getSizes();
                    for (int i=0; i<sizes.size(); ++i)
                        max_size = Math.max(max_size,  sizes.getInt(i));
                }
            }
            g.writeNumberField("pvs", pvs);
            g.writeNumberField("arrays", arrays);
            g.writeNumberField("max_size", max_size);

            g.writeEndObject();
        }
        g.writeEndArray();
        g.writeEndObject();
	}
}
