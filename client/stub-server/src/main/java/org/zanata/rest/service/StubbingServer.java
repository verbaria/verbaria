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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone runner for the stub Zanata REST server.
 *
 * Starts a Spring Boot (embedded Tomcat) server that exposes the mock REST
 * resources used by the client tests. Useful for manual CLI smoke testing:
 *
 * <pre>
 *   $ ./run.sh stub-server &amp;
 *   $ ./run.sh cli list-remote --url=http://localhost:8888 \
 *                              --username=admin --key=abcd1234 \
 *                              --project=sample --project-version=1
 * </pre>
 *
 * The server port defaults to 8888 (see application.properties) and may be
 * overridden via {@code --server.port=NNNN} on the command line, e.g.:
 *
 * <pre>
 *   $ java -jar stub-server.jar --server.port=9090
 * </pre>
 */
@SpringBootApplication
public class StubbingServer {

    public static void main(String[] args) {
        SpringApplication.run(StubbingServer.class, args);
    }
}
