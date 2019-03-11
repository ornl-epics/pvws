/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws;

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

/** Web application context
 *  @author Kay Kasemir
 */
@WebListener
public class PVWebSocketContext implements ServletContextListener
{
    public static final Logger logger = Logger.getLogger(WebSocket.class.getPackage().getName());

    public static final JsonFactory json_factory = new JsonFactory();

    private static final Set<WebSocket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());

    public static void register(final WebSocket socket)
    {
        sockets.add(socket);
    }

    @Override
    public void contextInitialized(final ServletContextEvent ev)
    {
        final ServletContext context = ev.getServletContext();

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " started");
        logger.log(Level.INFO, "===========================================");

    }

    @Override
    public void contextDestroyed(final ServletContextEvent ev)
    {
        final ServletContext context = ev.getServletContext();


        // Dispose all web sockets, i.e. close all PVs
        for (final WebSocket socket : sockets)
            socket.dispose();
        sockets.clear();

        if (! PVPool.getPVReferences().isEmpty())
            for (final ReferencedEntry<PV> ref : PVPool.getPVReferences())
            {
                logger.log(Level.WARNING, "Unrelease PV " + ref.getEntry().getName());
                PVPool.releasePV(ref.getEntry());
            }

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " shut down");
        logger.log(Level.INFO, "===========================================");
    }
}
