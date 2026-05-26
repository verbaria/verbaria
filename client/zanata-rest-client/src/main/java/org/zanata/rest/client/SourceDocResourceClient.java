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

import java.util.List;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.zanata.rest.RestUtil;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;

public class SourceDocResourceClient {
    private static final String BASE =
            "projects/p/{proj}/iterations/i/{iter}";

    private final RestClientFactory factory;
    private final String project;
    private final String projectVersion;

    SourceDocResourceClient(RestClientFactory factory, String project,
            String projectVersion) {
        this.factory = factory;
        this.project = project;
        this.projectVersion = projectVersion;
    }

    public List<ResourceMeta> getResourceMeta(Set<String> extensions) {
        List<ResourceMeta> body = rest().get()
                .uri(uri -> {
                    var b = uri.path(BASE + "/r");
                    if (extensions != null && !extensions.isEmpty()) {
                        b.queryParam("ext", extensions.toArray());
                    }
                    return b.build(project, projectVersion);
                })
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<ResourceMeta>>() {});
        return body == null ? List.of() : body;
    }

    public Resource getResource(String docId, Set<String> extensions) {
        Object[] extArr = extensions == null ? new Object[0]
                : extensions.toArray();
        try {
            return rest().get()
                    .uri(uri -> uri.path(BASE + "/resource")
                            .queryParam("docId", docId)
                            .queryParam("ext", extArr)
                            .build(project, projectVersion))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Resource.class);
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            return rest().get()
                    .uri(uri -> uri.path(BASE + "/r/{docId}")
                            .queryParam("ext", extArr)
                            .build(project, projectVersion, idNoSlash))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Resource.class);
        }
    }

    public String putResource(String docId, Resource resource,
            Set<String> extensions, boolean copyTrans) {
        Object[] extArr = extensions == null ? new Object[0]
                : extensions.toArray();
        try {
            return rest().put()
                    .uri(uri -> uri.path(BASE + "/resource")
                            .queryParam("docId", docId)
                            .queryParam("ext", extArr)
                            .queryParam("copyTrans", copyTrans)
                            .build(project, projectVersion))
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resource)
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            return rest().put()
                    .uri(uri -> uri.path(BASE + "/r/{docId}")
                            .queryParam("ext", extArr)
                            .queryParam("copyTrans", copyTrans)
                            .build(project, projectVersion, idNoSlash))
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resource)
                    .retrieve()
                    .body(String.class);
        }
    }

    public String deleteResource(String docId) {
        try {
            String out = rest().delete()
                    .uri(uri -> uri.path(BASE + "/resource")
                            .queryParam("docId", docId)
                            .build(project, projectVersion))
                    .retrieve()
                    .body(String.class);
            return out == null ? "" : out;
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            String out = rest().delete()
                    .uri(uri -> uri.path(BASE + "/r/{docId}")
                            .build(project, projectVersion, idNoSlash))
                    .retrieve()
                    .body(String.class);
            return out == null ? "" : out;
        }
    }

    private RestClient rest() {
        return factory.getSpringRestClient();
    }
}
