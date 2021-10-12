/*******************************************************************************
 * Copyright (c) 2019-2020 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;
import org.phoebus.pv.RefCountMap.ReferencedEntry;

import com.fasterxml.jackson.core.JsonFactory;

import pvws.ws.WebSocket;

/** Web application context, tracks all {@link WebSocket}s
 *  @author Kay Kasemir
 */
@WebListener
public class PVWebSocketContext implements ServletContextListener
{
    public static final Logger logger = Logger.getLogger(WebSocket.class.getPackage().getName());

    public static final JsonFactory json_factory = new JsonFactory();

    private static final Set<WebSocket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());

    public static Instant start_time;

    @Override
    public void contextInitialized(final ServletContextEvent ev)
    {
        final ServletContext context = ev.getServletContext();

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " started");
        final StringBuilder buf = new StringBuilder();
        buf.append("Environment:");
        for (String name : new String[]
                           {
                               "EPICS_CA_ADDR_LIST",
                               "EPICS_CA_MAX_ARRAY_BYTES",
                               "PV_THROTTLE_MS",
                               "PV_ARRAY_THROTTLE_MS",
                               "PV_WRITE_SUPPORT"
                            })
            buf.append("\n").append(name).append(" = ").append(System.getenv(name));
        logger.log(Level.INFO, buf.toString());
        logger.log(Level.INFO, "===========================================");

        start_time = Instant.now();
    }

    /** @param socket {@link WebSocket} to track */
    public static void register(final WebSocket socket)
    {
        sockets.add(socket);
    }

    /** @param socket {@link WebSocket} to track no more */
    public static void unregister(final WebSocket socket)
    {
        sockets.remove(socket);
    }

    public static Collection<WebSocket> getSockets()
    {
        return sockets;
    }

    @Override
    public void contextDestroyed(final ServletContextEvent ev)
    {
        final ServletContext context = ev.getServletContext();

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " shut down");

        // Dispose all web sockets that did not self-close
        if (! sockets.isEmpty())
        {
            logger.log(Level.INFO, "Web sockets that did not close/unregister:");
            for (final WebSocket socket : sockets)
                socket.dispose();
            sockets.clear();
        }
        if (! PVPool.getPVReferences().isEmpty())
            for (final ReferencedEntry<PV> ref : PVPool.getPVReferences())
            {
                logger.log(Level.WARNING, "Unreleased PV " + ref.getEntry().getName());
                PVPool.releasePV(ref.getEntry());
            }

        logger.log(Level.INFO, "===========================================");
    }
}
