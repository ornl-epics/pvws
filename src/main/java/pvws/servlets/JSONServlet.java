/*******************************************************************************
 * Copyright (c) 2019-2020 UT-Battelle, LLC.
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
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.JsonGenerator;

/** Base for Servlet that returns JSON
 *  @author Kay Kasemir
 */
public abstract class JSONServlet extends HttpServlet
{
	private static final long serialVersionUID = 1L;

	/** GET /socket : Return info about all sockets and their PVs */
	@Override
    final protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException
	{
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        final JsonGenerator g = json_factory.createGenerator(buf);
        writeJson(request, g);
        g.flush();

        response.setContentType("application/json");
        final PrintWriter writer = response.getWriter();
        writer.append(buf.toString());
	}

	/** Derived class implements this to fill the JSON that's returned by servlet
	 *
	 *  @param request {@link HttpServletRequest}
	 *  @param g {@link JsonGenerator}
	 *  @throws IOException on error
	 */
    abstract protected void writeJson(final HttpServletRequest request, final JsonGenerator g) throws IOException;
}
