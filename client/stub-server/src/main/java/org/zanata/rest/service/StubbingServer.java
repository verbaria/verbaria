/*
 * Standalone runner for the stub Zanata REST server.
 *
 * Starts a Jetty 11 server that exposes the mock REST resources used by the
 * client tests. Useful for manual CLI smoke testing:
 *
 *   $ ./run.sh stub-server &
 *   $ ./run.sh cli list-remote --url=http://localhost:8888 \
 *                              --username=admin --key=abcd1234 \
 *                              --project=sample --project-version=1
 */
package org.zanata.rest.service;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.server.servlet.HttpServlet30Dispatcher;

public final class StubbingServer {

    private StubbingServer() {}

    public static void main(String[] args) throws Exception {
        int port = 8888;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[i + 1]);
            }
        }

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");

        ServletHolder holder = new ServletHolder(new HttpServlet30Dispatcher());
        holder.setInitParameter("jakarta.ws.rs.Application",
                MockResourcesApplication.class.getCanonicalName());
        // Mount under /rest/* so the Zanata client URLs (which append "rest/"
        // to the base URL) resolve correctly without further config.
        holder.setInitParameter("resteasy.servlet.mapping.prefix", "/rest");
        context.addServlet(holder, "/rest/*");

        server.setHandler(context);
        server.setStopAtShutdown(true);

        server.start();
        System.out.println();
        System.out.println("Zanata stub-server running on " + server.getURI() + "rest/");
        System.out.println("(use this URL as --url for the CLI; alternatively " + server.getURI() + " works too — the CLI appends 'rest/')");
        System.out.println("Press Ctrl+C to stop.");
        server.join();
    }
}
