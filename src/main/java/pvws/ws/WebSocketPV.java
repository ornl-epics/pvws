/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.ws;

import java.util.concurrent.TimeUnit;

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
    private static final int THROTTLE_MS = 1000;
    private final String name;
    private final WebSocket socket;
    private volatile PV pv;
    private volatile Disposable subscription;
    private volatile VType last_value = null;

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
        pv = PVPool.getPV(name);
        subscription = pv.onValueEvent()
                         .throttleLatest(THROTTLE_MS, TimeUnit.MILLISECONDS)
                         .subscribe(this::handleUpdates);
    }

    private void handleUpdates(final VType value)
    {
        socket.sendUpdate(name, value, last_value == null);
        last_value = value;
    }

    /** Close PV */
    public void dispose()
    {
        subscription.dispose();
        subscription = null;
        PVPool.releasePV(pv);
        pv = null;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
