/*
 * Copyright 2026, verbaria.org and Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.zanata.rest.service;

import java.net.URI;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Starts an embedded Spring Boot server hosting the stubbed Zanata REST
 * resources. All resource implementations either return a fixed response or,
 * if not used by the client at the moment, throw an exception.
 *
 * The server binds to a random port on first use; the actual base URI is
 * available via {@link #getServerBaseUri()}.
 *
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class StubbingServerRule implements TestRule {
    private static ConfigurableApplicationContext context;
    private static URI baseUri;

    public StubbingServerRule() {
        startServerIfRequired();
    }

    private static synchronized void startServerIfRequired() {
        if (context != null && context.isActive()) {
            return;
        }
        // Bind to a random port; disable banner / startup logging noise so
        // tests stay quiet. Using SERVLET so we get the embedded Tomcat.
        context = new SpringApplicationBuilder(StubbingServer.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "spring.main.log-startup-info=false")
                .run();
        Integer port = context.getEnvironment()
                .getProperty("local.server.port", Integer.class);
        if (port == null) {
            throw new IllegalStateException(
                    "Spring Boot did not expose local.server.port");
        }
        baseUri = URI.create("http://localhost:" + port + "/");

        // The Spring context is a JVM-wide singleton for the test run;
        // make sure it's torn down cleanly when the JVM exits.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (context != null && context.isActive()) {
                    context.close();
                }
            } catch (Exception ignored) {
                // best-effort
            }
        }));
    }

    @Override
    public Statement apply(final Statement base, Description description) {
        return base;
    }

    public URI getServerBaseUri() {
        return baseUri;
    }
}
