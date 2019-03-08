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
 *  @author Kay Kasemir
 */
public class WebSocketPV
{
    private final String name;
    private Disposable subscription;

    public WebSocketPV(final String name)
    {
        this.name = name;
    }

    public String getName()
    {
        return name;
    }

    public void start() throws Exception
    {
        final PV pv = PVPool.getPV(name);
        subscription = pv.onValueEvent()
                         .throttleLatest(1000, TimeUnit.MILLISECONDS)
                         .subscribe(this::handleUpdates);
    }

    private void handleUpdates(final VType value)
    {
        // TODO Send data to web socket client
        System.out.println(name + " = " + value);
    }

    public void dispose()
    {
        subscription.dispose();
    }

    @Override
    public String toString()
    {
        return name;
    }
}
