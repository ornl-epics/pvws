/*******************************************************************************
 * Copyright (c) 2017 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv.ca;

import static org.phoebus.pv.PV.logger;

import java.util.logging.Level;

import gov.aps.jca.Monitor;

/** Preferences for JCA
 *
 *  @author Kay Kasemir
 */
@SuppressWarnings("nls")
public class JCA_Preferences
{
    private static final JCA_Preferences instance = new JCA_Preferences();

    private final int monitor_mask = Monitor.VALUE | Monitor.ALARM;

    private final boolean dbe_property_supported = true;

    private final Boolean var_array_supported = null; // Auto

    private final int large_array_threshold = 100000;

    /** Initialize */
    private JCA_Preferences()
    {
        try
        {
            installPreferences();
        }
        catch (final Exception ex)
        {
            logger.log(Level.SEVERE, "Preferences Error", ex);
        }
    }

    /** Update the JCA/CAJ related properties from preferences
     *  @throws Exception on error
     */
    public void installPreferences() throws Exception
    {
        // Set the 'CAJ' and 'JNI' copies of the settings
        setSystemProperty("com.cosylab.epics.caj.CAJContext.use_pure_java", "true");

        final String addr_list = System.getenv("EPICS_CA_ADDR_LIST");
        if (addr_list != null)
        {
            setSystemProperty("com.cosylab.epics.caj.CAJContext.addr_list", addr_list);
            setSystemProperty("gov.aps.jca.jni.JNIContext.addr_list", addr_list);
            logger.log(Level.INFO, "EPICS_CA_ADDR_LIST: " + addr_list);
        }

//        final String auto_addr = System.getenv("EPICS_CA_AUTO_ADDR_LIST");
//        setSystemProperty("com.cosylab.epics.caj.CAJContext.auto_addr_list", auto_addr);
//        setSystemProperty("gov.aps.jca.jni.JNIContext.auto_addr_list", auto_addr);

        final String max_array_bytes = System.getenv("EPICS_CA_MAX_ARRAY_BYTES");
        if (max_array_bytes != null)
        {
            setSystemProperty("com.cosylab.epics.caj.CAJContext.max_array_bytes", max_array_bytes);
            setSystemProperty("gov.aps.jca.jni.JNIContext.max_array_bytes", max_array_bytes);
            logger.log(Level.INFO, "EPICS_CA_MAX_ARRAY_BYTES: " + max_array_bytes);
        }

        // gov.aps.jca.event.QueuedEventDispatcher avoids
        // deadlocks when calling JCA while receiving JCA callbacks.
        // But JCA_PV avoids deadlocks, and QueuedEventDispatcher is faster
        setSystemProperty("gov.aps.jca.jni.ThreadSafeContext.event_dispatcher",
                          "gov.aps.jca.event.DirectEventDispatcher");
    }

    /** Sets property from preferences to System properties only if property
     *  value is not null or empty string.
     *  @param prop System property name
     *  @param value CSS preference name
     */
    private void setSystemProperty(final String prop, final String value)
    {
        if (value == null  ||  value.isEmpty())
            return;

        logger.log(Level.FINE, "{0} = {1}", new Object[] { prop, value });

        System.setProperty(prop, value);
    }

    /** @return Singleton instance */
    public static JCA_Preferences getInstance()
    {
        return instance;
    }

    /** @return Mask used to create CA monitors (subscriptions) */
    public int getMonitorMask()
    {
        return monitor_mask;
    }

    /** @return whether metadata updates are enabled */
    public boolean isDbePropertySupported()
    {
        return dbe_property_supported;
    }

    /** @return Whether variable array should be supported (true/false), or auto-detect (<code>null</code>) */
    public Boolean isVarArraySupported()
    {
        return var_array_supported;
    }

    public int largeArrayThreshold()
    {
        return large_array_threshold;
    }
}
