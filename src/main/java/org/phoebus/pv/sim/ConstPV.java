/*******************************************************************************
 * Copyright (c) 2017-2018 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 ******************************************************************************/
package org.phoebus.pv.sim;

import java.util.List;

import org.phoebus.pv.PV;

/** Simulated PV for flipflop
 *  @author Kay Kasemir, based on similar code in org.csstudio.utility.pv and diirt
 */
@SuppressWarnings("nls")
public class ConstPV extends SimulatedStringPV
{
    private String value = "";

    public static PV forParameters(final String name, final String parameters) throws Exception
    {
        if (!parameters.isEmpty())
            return new ConstPV(name, parameters);
        throw new Exception("sim://const needs one parameter");
    }

    public ConstPV(final String name, final String value)
    {
        super(name);
        this.value = value;
        start(10);
    }
    
    @Override
    public String compute()
    {
        return value;
    }
}
