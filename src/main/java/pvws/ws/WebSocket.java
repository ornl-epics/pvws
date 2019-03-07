package pvws.ws;

import java.nio.ByteBuffer;
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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

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

	// @PathParam("some-id")
	@OnMessage
	public void onMessage(final String message, final Session session)
	{
		ActivePVEndpoints.trackUpdate(session);
		logger.log(Level.FINER, "Received: " + message);

		try
		{
			final Basic remote = session.getBasicRemote();
			remote.sendText("Got your message...\n");

			final JsonParser jp = fs.createParser(message);
			if (jp.nextToken() != JsonToken.START_OBJECT)
				throw new Exception("Expected JSON, got " + message);
			while (jp.nextToken() != JsonToken.END_OBJECT)
			{
				final String field = jp.getCurrentName();
				jp.nextToken();
				if ("name".equals(field))
				{
					final String name = jp.getText();
					remote.sendText("Hello, " + name + "\n");
				}
				else
					logger.log(Level.WARNING, "Ignoring " + field + " in " + message);
			}

			remote.sendPing(ByteBuffer.allocate(0));
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
