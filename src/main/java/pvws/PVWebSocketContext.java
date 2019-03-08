/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws;

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

        // Close all PVs
        // TODO dispose all subscriptions, i.e. ask all web sockets to close down
        for (final ReferencedEntry<PV> ref : PVPool.getPVReferences())
        {
            logger.log(Level.FINE, "Releasing " + ref.getEntry().getName());
            PVPool.releasePV(ref.getEntry());
        }

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " shut down");
        logger.log(Level.INFO, "===========================================");
    }
}
