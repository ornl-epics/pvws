/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import static pvws.PVWebSocketContext.json_factory;
import static pvws.PVWebSocketContext.logger;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pvws.PVWebSocketContext;

/** Web socket, handles {@link WebSocketPV}s for one web client
 *  @author Kay Kasemir
 */
@ServerEndpoint(value="/pv")
public class WebSocket
{
    /** Track when the last message was received by web client */
    private volatile long last_client_message = 0;

	/** Map of PV name to PV */
	private final ConcurrentHashMap<String, WebSocketPV> pvs = new ConcurrentHashMap<>();

	public WebSocket()
	{
	    // Constructor, register with PVWebSocketContext
	    PVWebSocketContext.register(this);
	}

	private void trackClientUpdate()
	{
	    last_client_message = System.currentTimeMillis();
	}

	// TODO SessionManager:
	// Periodically 'ping'
	// Track time of last interaction
	@OnOpen
	public void onOpen(final Session session, final EndpointConfig config)
	{
		logger.log(Level.FINE, "Opening web socket " + session.getRequestURI() + " ID " + session.getId());
		trackClientUpdate();
	}

	@OnClose
	public void onClose(final Session session, final CloseReason reason)
	{
	    dispose();
		logger.log(Level.FINE, "Web socket closed");
		last_client_message = 0;
	}

	@OnMessage
	public void onPong(final PongMessage message, final Session session)
	{
		logger.log(Level.FINER, "Got pong");
		trackClientUpdate();
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
	    trackClientUpdate();
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
                for (final String name : getPVs(message, json))
                {
                    pvs.computeIfAbsent(name, n ->
                    {
                        logger.log(Level.FINER, "Subscribe to " + name);
                        final WebSocketPV pv = new WebSocketPV(name);
                        try
                        {
                            pv.start();
                        }
                        catch (final Exception ex)
                        {
                            logger.log(Level.WARNING, "Cannot start PV " + name, ex);
                        }
                        return pv;
                    });
                }
                break;
            case "clear":
                for (final String name : getPVs(message, json))
                {
                    final WebSocketPV pv = pvs.remove(name);
                    if (pv != null)
                    {
                        logger.log(Level.FINER, "Clear " + name);
                        pv.dispose();
                    }
                }
                break;
            case "list":
                {
                    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    final JsonGenerator g = json_factory.createGenerator(buf);
                    g.writeStartObject();
                    g.writeStringField("type", "list");
                    g.writeArrayFieldStart("pvs");
                    for (final WebSocketPV pv : pvs.values())
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
                throw new Exception("Unknown message type: " + message);
            }
		}
		catch (final Exception ex)
		{
            logger.log(Level.WARNING, "Message error", ex);
		}
	}

	@OnError
	public void onError(final Throwable ex)
	{
        logger.log(Level.WARNING, "Web Socket error", ex);
	}

	/** Clears all PVs
	 *
	 *  <p>Web socket calls this onClose(),
	 *  but context may also call this again just in case
	 */
	public void dispose()
	{
	    if (! pvs.isEmpty())
	    {
            logger.log(Level.INFO, "Disposing web socket PVs:");
            for (final WebSocketPV pv : pvs.values())
            {
                logger.log(Level.INFO, "Closing " + pv);
                pv.dispose();
            }
            pvs.clear();
	    }
	}
}
