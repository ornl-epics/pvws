/*******************************************************************************
 * Copyright (c) 2019-2020 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import static pvws.PVWebSocketContext.logger;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import org.epics.vtype.Array;
import org.epics.vtype.VType;
import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;

import io.reactivex.disposables.Disposable;

/** Web socket PV
 *
 *  <p>Connects to {@link PV}, updates {@link WebSocket}
 *
 *  @author Kay Kasemir
 */
public class WebSocketPV
{
    /** Value throttle */
    private static final int THROTTLE_MS;

    /** Array value throttle */
    private static final int ARRAY_THROTTLE_MS;

    /** Support writing? */
    private static final boolean PV_WRITE_SUPPORT;

    private final String name;
    private final WebSocket socket;
    private volatile PV pv;
    private AtomicReference<Disposable> subscription = new AtomicReference<>(), array_subscription = new AtomicReference<>(),
                                        subscription_access = new AtomicReference<>();
    private volatile boolean subscribed_for_array = false;
    private volatile VType last_value = null;
    private volatile boolean last_readonly = true;

    static
    {
        String spec = System.getenv("PV_THROTTLE_MS");
        if (spec == null)
            THROTTLE_MS = 1000;
        else
            THROTTLE_MS = Integer.parseInt(spec);

        spec = System.getenv("PV_ARRAY_THROTTLE_MS");
        if (spec == null)
            ARRAY_THROTTLE_MS = 10000;
        else
            ARRAY_THROTTLE_MS = Integer.parseInt(spec);

        spec = System.getenv("PV_WRITE_SUPPORT");
        PV_WRITE_SUPPORT = "true".equalsIgnoreCase(spec);

        logger.log(Level.INFO, "PV_THROTTLE_MS = " + THROTTLE_MS);
        logger.log(Level.INFO, "PV_ARRAY_THROTTLE_MS = " + ARRAY_THROTTLE_MS);
        logger.log(Level.INFO, "PV_WRITE_SUPPORT = " + PV_WRITE_SUPPORT);
    }

    /** @param name PV name
     *  @param socket Socket to notify about value updates
     */
    public WebSocketPV(final String name, final WebSocket socket)
    {
        this.name = name;
        this.socket = socket;
    }

    /** @return PV name */
    public String getName()
    {
        return name;
    }

    /** Start PV
     *  @throws Exception on error
     *  @see #dispose()
     */
    public void start() throws Exception
    {
        subscribed_for_array = false;
        pv = PVPool.getPV(name);
        // Subscribe at the 'normal' throttling rate.
        subscription.set(pv.onValueEvent()
                           .throttleLatest(THROTTLE_MS, TimeUnit.MILLISECONDS)
                           .subscribe(this::handleUpdates));
        subscription_access.set(pv.onAccessRightsEvent()
                                   .throttleLatest(THROTTLE_MS, TimeUnit.MILLISECONDS)
                                   .subscribe(this::handleAccessChanges));
    }

    /** Handle change in value
     *  @param value Latest value
     */
    private void handleUpdates(final VType value)
    {
        if (value instanceof Array  && !subscribed_for_array)
        {
            // If the data turns out to be array values,
            // re-subscribe at a (slower) 'array' rate.
            // Problem is that subscribe() above may end up calling this update handler
            // before 'subscription' is assigned,
            // so we won't be able to cancel that subscription right away.
            // Instead, we'll create a separate 'array_subscription',
            // and in a later update we then dispose the initial subscription.
            final Array array = (Array) value;
            logger.log(Level.FINE, () -> "Re-subscribing to array " + name + ", " + array.getSizes());

            subscribed_for_array = true;
            array_subscription.set(pv.onValueEvent()
                                     .throttleLatest(ARRAY_THROTTLE_MS, TimeUnit.MILLISECONDS)
                                     .subscribe(this::handleUpdates));
            return;
        }

        if (subscribed_for_array)
        {
            final Disposable sub = subscription.getAndSet(null);
            if (sub != null)
            {
                logger.log(Level.FINE, () -> "Closing non-array subscription for " + name);
                sub.dispose();
            }
        }

        socket.sendUpdate(name, value, last_value, last_readonly, pv.isReadonly() || !PV_WRITE_SUPPORT);
        last_value = value;
        last_readonly = pv.isReadonly();
    }

    /** Handle change in access permissions
     *  @param readonly Latest access mode
     */
    private void handleAccessChanges(final Boolean readonly)
    {
        socket.sendUpdate(name, last_value, last_value, last_readonly, pv.isReadonly() || !PV_WRITE_SUPPORT);
        last_readonly = pv.isReadonly();
    }

    /** @return Most recent value or null */
    public VType getLastValue()
    {
        return last_value;
    }

    /** @param new_value Value to write to PV
     *  @throws Exception on error
     */
    public void write(Object new_value) throws Exception
    {
        if (PV_WRITE_SUPPORT)
            pv.write(new_value);
        else
            throw new Exception("PV_WRITE_SUPPORT is disabled");
    }

    /** Close PV */
    public void dispose()
    {
        Disposable sub = array_subscription.getAndSet(null);
        if (sub != null)
        {
            logger.log(Level.FINE, () -> "Closing array subscription for " + name);
            sub.dispose();
        }

        sub = subscription.getAndSet(null);
        if (sub != null)
        {
            logger.log(Level.FINE, () -> "Closing subscription for " + name);
            sub.dispose();
        }

        sub = subscription_access.getAndSet(null);
        if (sub != null)
        {
            logger.log(Level.FINE, () -> "Closing access subscription for " + name);
            sub.dispose();
        }

        // PV may never have been created for invalid PV name...
        if (pv != null)
            PVPool.releasePV(pv);
        pv = null;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
