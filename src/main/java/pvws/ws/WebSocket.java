/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import java.nio.ByteBuffer;
import java.util.Iterator;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Web socket for PV subscriptions
 *  @author Kay Kasemir
 */
@ServerEndpoint(value="/pv")
public class WebSocket
{
	public static final Logger logger = Logger.getLogger(WebSocket.class.getPackage().getName());

	private static final JsonFactory fs = new JsonFactory();

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

	@OnMessage
	public void onMessage(final String message, final Session session)
	{
		ActivePVEndpoints.trackUpdate(session);
		logger.log(Level.FINER, "Received: " + message);

		try
		{
			final Basic remote = session.getBasicRemote();

			final JsonNode json = new ObjectMapper(fs).readTree(message);
			JsonNode node = json.path("type");
			if (node.isMissingNode())
			    throw new Exception("Missing 'type' in " + message);
	        final String type = node.asText();
            switch (type)
            {
            // Support 'monitor' for compatibility with epics2web
            case "monitor":
            case "subscribe":
                node = json.path("pvs");
                if (node.isMissingNode())
                    throw new Exception("Missing 'pvs' in " + message);
                final Iterator<JsonNode> nodes = node.elements();
                while (nodes.hasNext())
                {
                    final String pv = nodes.next().asText();
                    // TODO
                    System.out.println("Subscribe to " + pv);
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
