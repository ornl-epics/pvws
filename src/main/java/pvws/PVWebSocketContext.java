/*******************************************************************************
 * Copyright (c) 2019-2026 UT-Battelle, LLC.
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
import java.util.prefs.Preferences;

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
@SuppressWarnings("nls")
@WebListener
public class PVWebSocketContext implements ServletContextListener
{
    /** Shared logger */
    public static final Logger logger = Logger.getLogger(WebSocket.class.getPackage().getName());

    /** Shared JSON factory */
    public static final JsonFactory json_factory = new JsonFactory();

    private static final Set<WebSocket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<WebSocket, Boolean>());

    /** Context start time */
    public static Instant start_time;

    @Override
    public void contextInitialized(final ServletContextEvent ev)
    {
        final ServletContext context = ev.getServletContext();

        logger.log(Level.INFO, "===========================================");
        logger.log(Level.INFO, context.getContextPath() + " started");

        // Set default type in preferences before PVPool reads the preferences
        String default_type = System.getenv("PV_DEFAULT_TYPE");
        if (default_type != null  &&  !default_type.isEmpty())
            Preferences.userRoot().node("/org/phoebus/pv").put("default", default_type);

        logger.log(Level.INFO, "Supported PV types: " + PVPool.getSupportedPrefixes());

        // Configure JCA/CAJ to use environment vars, not java properties or preferences
        System.setProperty("jca.use_env", "true");
        for (String name : new String[]
                           {
                               "PV_DEFAULT_TYPE",
                               "PV_THROTTLE_MS",
                               "PV_ARRAY_THROTTLE_MS",
                               "PV_WRITE_SUPPORT",
                               "EPICS_CA_ADDR_LIST",
                               "EPICS_CA_AUTO_ADDR_LIST",
                               "EPICS_CA_MAX_ARRAY_BYTES",
                               "EPICS_PVA_ADDR_LIST",
                               "EPICS_PVA_AUTO_ADDR_LIST"
                            })
            logger.log(Level.INFO, name + " = " + System.getenv(name));

        try
        {
            // We take settings from environment variables.
            // Based on version, core-pva's PVA_Preferences might read from the default
            // Preferences implementation,
            // for example the Mac prefs when on Mac which persist in ~/Library/preferences/xxxx.plist.
            // We would then use the last settings written by some phoebus tool.
            // --> Delete those!
            Class<?> cls = Class.forName("org.phoebus.pv.pva.PVA_Preferences");
            Preferences prefs = Preferences.userNodeForPackage(cls);
            for (String setting : new String[]
                                  {
                                    "epics_pva_addr_list",
                                    "epics_pva_auto_addr_list",
                                    "epics_pva_name_servers",
                                    "epics_pva_server_port",
                                    "epics_pva_broadcast_port",
                                    "epics_pva_conn_tmo",
                                    "epics_pva_tcp_socket_tmo",
                                    "epics_pva_max_array_formatting",
                                    "epics_pva_send_buffer_size"
                                })
            {
                String value = prefs.get(setting, null);
                if (value != null  &&  !value.isEmpty())
                {
                    logger.log(Level.INFO, "Clearing  " + setting + " = " + value + " from PVA_Preferences");
                    prefs.remove(setting);
                }
            }
        }
        catch (ClassNotFoundException ex)
        {
            logger.log(Level.INFO, "PVA_Preferences class not found, not clearing persisted settings");
        }

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

    /** @return Currently known sockets */
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
