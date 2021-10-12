/*******************************************************************************
 * Copyright (c) 2019-2020 UT-Battelle, LLC.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;

import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;
import org.phoebus.pv.RefCountMap.ReferencedEntry;

import com.fasterxml.jackson.core.JsonGenerator;

/** Servlet to list PV Pool entries
 *  @author Kay Kasemir
 */
@WebServlet("/pool")
public class PoolServlet extends JSONServlet
{
	private static final long serialVersionUID = 1L;

	/** GET /pool : Return info PVs in pool */
	@Override
    protected void writeJson(final HttpServletRequest request, final JsonGenerator g) throws IOException
	{
        g.writeStartArray();
        for (final ReferencedEntry<PV> ref : PVPool.getPVReferences())
        {
            g.writeStartObject();
            g.writeNumberField("refs", ref.getReferences());
            g.writeStringField("pv", ref.getEntry().getName());
            g.writeEndObject();
        }
        g.writeEndArray();
	}
}
