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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
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

import org.epics.vtype.VType;

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

    /** Queue of messages for the client.
     *
     *  <p>Multiple threads concurrently writing to the socket results in
     *  IllegalStateException "remote endpoint was in state [TEXT_FULL_WRITING]"
     *  All writes are thus performed by just one thread off this queue.
     */
    private final ArrayBlockingQueue<String> write_queue = new ArrayBlockingQueue<String>(2048);

    /** Thread that writes messages until an {@link #EXIT_MESSAGE} is queued. */
    private final Thread write_thread;
    private static final String EXIT_MESSAGE = "EXIT";

    private volatile Session session = null;

    /** Map of PV name to PV */
	private final ConcurrentHashMap<String, WebSocketPV> pvs = new ConcurrentHashMap<>();

	public WebSocket()
	{
	    // Constructor, register with PVWebSocketContext
	    PVWebSocketContext.register(this);

	    write_thread = new Thread(this::writeQueuedMessages, "Write Thread");
	    write_thread.setDaemon(true);
	    write_thread.start();
	}

	/** @return Session ID */
	public String getId()
	{
	    final Session safe = session;
	    return safe == null ? "None" : safe.getId();
	}

	/** @return Timestamp (ms since epoch) of last client message */
	public long getLastClientMessage()
	{
	    return last_client_message;
	}

	/** @return {@link WebSocketPV}s */
	public Collection<WebSocketPV> getPVs()
	{
	    return Collections.unmodifiableCollection(pvs.values());
	}

    /** @return Number of queued messages */
	public int getQueuedMessageCount()
	{
	    return write_queue.size();
	}

	private void queueMessage(final String message)
	{
	    if (! write_queue.offer(message))
	        logger.log(Level.WARNING, "Cannot queue message " + message);
	}

	private void writeQueuedMessages()
	{
	    while (true)
	    {
	        final String message;
	        try
	        {
	            message = write_queue.take();
	        }
	        catch (final InterruptedException ex)
	        {
	            return;
	        }

	        if (message == EXIT_MESSAGE)
	        {
	            logger.log(Level.FINE, "Exiting write thread");
	            return;
	        }

	        // Check if we should exit the thread
	        final Session safe_session = session;

	        try
	        {
	            if (safe_session == null)
	                throw new Exception("No session");
	            if (! safe_session.isOpen())
	                throw new Exception("Session closed");
                safe_session.getBasicRemote().sendText(message);
            }
	        catch (final Exception ex)
	        {
	            logger.log(Level.WARNING, "Cannot write '" + message + "'", ex);
            }
	    }
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
		this.session = session;
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
                        final WebSocketPV pv = new WebSocketPV(name, this);
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
                    queueMessage(buf.toString());
                }
                break;
            case "ping":
                logger.log(Level.FINER, "Sending ping...");
                remote.sendPing(ByteBuffer.allocate(0));
                break;
            case "echo":
                queueMessage(message);
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

	/** @param name PV name for which to send an update
	 *  @param value Current value
	 *  @param last_value Previous value
	 */
	public void sendUpdate(final String name, final VType value, final VType last_value)
	{
	    try
	    {
    	    queueMessage(Vtype2Json.toJson(name, value, last_value));
	    }
	    catch (final Exception ex)
	    {
	        logger.log(Level.WARNING, "Cannot cannot send " + name + " = " + value, ex);
	    }
	}

	/** Clears all PVs
	 *
	 *  <p>Web socket calls this onClose(),
	 *  but context may also call this again just in case
	 */
	public void dispose()
	{
	    // Exit write thread
	    queueMessage(EXIT_MESSAGE);
	    if (! pvs.isEmpty())
	    {
            logger.log(Level.FINE, "Disposing web socket PVs:");
            for (final WebSocketPV pv : pvs.values())
            {
                logger.log(Level.FINE, "Closing " + pv);
                pv.dispose();
            }
            pvs.clear();
            PVWebSocketContext.unregister(this);
	    }
	    session = null;
	}
}
