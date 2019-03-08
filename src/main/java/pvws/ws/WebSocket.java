/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.RemoteEndpoint.Basic;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Web socket for PV subscriptions
 *  @author Kay Kasemir
 */
@ServerEndpoint(value="/pv")
public class WebSocket
{
	public static final Logger logger = Logger.getLogger(WebSocket.class.getPackage().getName());

	private static final JsonFactory json_factory = new JsonFactory();

	// TODO Other concurrent struct?
	private final List<WebSocketPV> pvs = new ArrayList<>();

	// TODO SessionManager:
	// Track all sessions: active? PVs?
	// Periodically 'ping'
	// Track time of last interaction
	@OnOpen
	public void onOpen(final Session session, final EndpointConfig config)
	{
		logger.log(Level.FINE, "Opening web socket");
		ActivePVEndpoints.trackUpdate(session);
	}

	@OnClose
	public void onClose(final Session session, final CloseReason reason)
	{
		logger.log(Level.FINE, "Web socket closed");
		ActivePVEndpoints.close(session);
	}

	@OnMessage
	public void onPong(final PongMessage message, final Session session)
	{
		logger.log(Level.FINER, "Got pong");
		ActivePVEndpoints.trackUpdate(session);
	}

	private List<String> getPVs(final String message, final JsonNode json) throws Exception
	{
	    final JsonNode node = json.path("pvs");
        if (node.isMissingNode())
            throw new Exception("Missing 'pvs' in " + message);
        final Iterator<JsonNode> nodes = node.elements();
        final List<String> pvs = new ArrayList<String>();
        while (nodes.hasNext())
            pvs.add(nodes.next().asText());
        return pvs;
	}

	@OnMessage
	public void onMessage(final String message, final Session session)
	{
		ActivePVEndpoints.trackUpdate(session);
		logger.log(Level.FINER, "Received: " + message + " on " + Thread.currentThread());

		try
		{
			final Basic remote = session.getBasicRemote();

			final ObjectMapper mapper = new ObjectMapper(json_factory);
            final JsonNode json = mapper.readTree(message);
			final JsonNode node = json.path("type");
			if (node.isMissingNode())
			    throw new Exception("Missing 'type' in " + message);
	        final String type = node.asText();
            switch (type)
            {
            // Support 'monitor' for compatibility with epics2web
            case "monitor":
            case "subscribe":
                for (final String pv : getPVs(message, json))
                {
                    // TODO
                    System.out.println("Subscribe to " + pv);
                    pvs.add(new WebSocketPV(pv));
                    System.out.println("PVs: " + pvs);
                }
                break;
            case "clear":
                for (final String pv : getPVs(message, json))
                {
                    // TODO
                    System.out.println("Clear " + pv);
                    pvs.removeIf(known -> known.getName().equals(pv));
                    System.out.println("PVs: " + pvs);
                }
                break;
            case "list":
                {
                    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    final JsonGenerator g = json_factory.createGenerator(buf);
                    g.writeStartObject();
                    g.writeStringField("type", "list");
                    g.writeArrayFieldStart("pvs");
                    for (final WebSocketPV pv : pvs)
                        g.writeString(pv.getName());
                    g.writeEndArray();
                    g.writeEndObject();
                    g.flush();
                    remote.sendText(buf.toString());
                }
                break;
            case "ping":
                logger.log(Level.FINER, "Sending ping...");
                remote.sendPing(ByteBuffer.allocate(0));
                break;
            case "echo":
                remote.sendText(message);
                break;
            default:
                logger.log(Level.WARNING, "Unknown message type: " + message);
            }
		}
		catch (final Exception ex)
		{
			ex.printStackTrace();
		}
	}

	@OnError
	public void onError(final Throwable ex)
	{
		ex.printStackTrace();
	}
}
