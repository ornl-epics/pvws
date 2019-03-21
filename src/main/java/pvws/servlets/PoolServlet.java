/*******************************************************************************
 * Copyright (c) 2019 Oak Ridge National Laboratory.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the LICENSE
 * which accompanies this distribution
 ******************************************************************************/
package pvws.servlets;

import static pvws.PVWebSocketContext.json_factory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.phoebus.pv.PV;
import org.phoebus.pv.PVPool;
import org.phoebus.pv.RefCountMap.ReferencedEntry;

import com.fasterxml.jackson.core.JsonGenerator;

/** Servlet to list PV Pool entries
 *  @author Kay Kasemir
 */
@WebServlet("/pool")
public class PoolServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;

	/** GET /pool : Return info PVs in pool */
	@Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
	{
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        g.writeStartArray();
        for (final ReferencedEntry<PV> ref : PVPool.getPVReferences())
        {
            g.writeStartObject();
            g.writeNumberField("refs", ref.getReferences());
            g.writeStringField("pv", ref.getEntry().getName());
            g.writeEndObject();
        }
        g.writeEndArray();
        g.flush();

        response.setContentType("application/json");
        final PrintWriter writer = response.getWriter();
        writer.append(buf.toString());
	}
}
