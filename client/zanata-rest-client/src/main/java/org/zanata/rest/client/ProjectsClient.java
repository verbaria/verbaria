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
import org.zanata.rest.MediaTypes;
import org.zanata.rest.dto.Project;
import org.zanata.rest.service.ProjectsResource;

public class ProjectsClient {
    private static final MediaType PROJECTS_JSON =
            MediaType.parseMediaType(MediaTypes.APPLICATION_ZANATA_PROJECTS_JSON);

    private final RestClientFactory factory;

    ProjectsClient(RestClientFactory factory) {
        this.factory = factory;
    }

    public Project[] getProjects() {
        return factory.getSpringRestClient().get()
                .uri(ProjectsResource.SERVICE_PATH)
                .accept(PROJECTS_JSON)
                .retrieve()
                .body(Project[].class);
    }
}
