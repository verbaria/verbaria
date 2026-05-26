/*
 * Copyright 2014, Red Hat, Inc. and individual contributors
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

package org.zanata.rest.client;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.zanata.rest.MediaTypes;
import org.zanata.rest.dto.Project;

public class ProjectClient {
    private static final MediaType PROJECT_JSON =
            MediaType.parseMediaType(MediaTypes.APPLICATION_ZANATA_PROJECT_JSON);

    private final RestClientFactory factory;
    private final String projectSlug;

    ProjectClient(RestClientFactory factory, String projectSlug) {
        this.factory = factory;
        this.projectSlug = projectSlug;
    }

    public Project get() {
        try {
            return factory.getSpringRestClient().get()
                    .uri("projects/p/{slug}", projectSlug)
                    .accept(PROJECT_JSON)
                    .retrieve()
                    .body(Project.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    public ResponseEntity<Void> put(Project project) {
        return factory.getSpringRestClient().put()
                .uri("projects/p/{slug}", projectSlug)
                .contentType(PROJECT_JSON)
                .body(project)
                .retrieve()
                .toBodilessEntity();
    }
}
