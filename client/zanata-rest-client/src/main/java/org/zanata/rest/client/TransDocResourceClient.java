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

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.zanata.common.LocaleId;
import org.zanata.rest.RestUtil;
import org.zanata.rest.dto.resource.TranslationsResource;

public class TransDocResourceClient {
    private static final String BASE =
            "projects/p/{proj}/iterations/i/{iter}";

    private final RestClientFactory factory;
    private final String project;
    private final String projectVersion;

    TransDocResourceClient(RestClientFactory factory, String project,
            String projectVersion) {
        this.factory = factory;
        this.project = project;
        this.projectVersion = projectVersion;
    }

    public ResponseEntity<TranslationsResource> getTranslations(String docId,
            LocaleId locale, Set<String> extensions, boolean createSkeletons,
            String eTag) {
        Object[] extArr = extensions == null ? new Object[0]
                : extensions.toArray();
        try {
            return rest().get()
                    .uri(uri -> uri.path(BASE + "/resource/translations/{loc}")
                            .queryParam("docId", docId)
                            .queryParam("ext", extArr)
                            .queryParam("skeletons", createSkeletons)
                            .queryParam("markTranslatedAsApproved", false)
                            .build(project, projectVersion, locale.getId()))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (eTag != null) {
                            h.set(HttpHeaders.IF_NONE_MATCH, eTag);
                        }
                    })
                    .retrieve()
                    .toEntity(TranslationsResource.class);
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            return rest().get()
                    .uri(uri -> uri.path(BASE + "/r/{docId}/translations/{loc}")
                            .queryParam("ext", extArr)
                            .queryParam("skeletons", createSkeletons)
                            .build(project, projectVersion, idNoSlash,
                                    locale.getId()))
                    .accept(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (eTag != null) {
                            h.set(HttpHeaders.IF_NONE_MATCH, eTag);
                        }
                    })
                    .retrieve()
                    .toEntity(TranslationsResource.class);
        }
    }

    private RestClient rest() {
        return factory.getSpringRestClient();
    }
}
