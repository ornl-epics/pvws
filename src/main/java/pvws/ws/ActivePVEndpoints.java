/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import javax.websocket.Session;

/** Active {@link WebSocket} sessions
 *
 *  <p>
 *  @author Kay Kasemir
 */
public class ActivePVEndpoints
{
	// Similar to epics2web.WebSocketSessionManager,
	// using session properties

	/** Session property to track time of last update */
	private static final String LAST_UPDATE_MS = "last_update_ms";

	public static void trackUpdate(final Session session)
	{
		session.getUserProperties().put(LAST_UPDATE_MS, System.currentTimeMillis());
	}

	public static void close(final Session session)
	{
		session.getUserProperties().remove(LAST_UPDATE_MS);
		// TODO Close PVs, stop sending updates
	}
}
