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

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.GlossaryEntry;
import org.zanata.rest.dto.QualifiedName;
import org.zanata.rest.service.ApiPaths;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class GlossaryClient {
    private final RestClientFactory factory;

    GlossaryClient(RestClientFactory factory) {
        this.factory = factory;
    }

    public void post(List<GlossaryEntry> glossaryEntries, LocaleId localeId,
            String qualifiedName) {
        factory.getSpringRestClient().post()
                .uri(uriBuilder -> uriBuilder
                        .path(ApiPaths.GLOSSARY_SERVICE_PATH)
                        .path("/entries")
                        .queryParam("locale", localeId.getId())
                        .queryParam("qualifiedName", qualifiedName)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(glossaryEntries)
                .retrieve()
                .toBodilessEntity();
    }

    public ResponseEntity<byte[]> downloadFile(String fileType,
            ImmutableList<String> transLang, String qualifiedName) {
        return factory.getSpringRestClient().get()
                .uri(uriBuilder -> {
                    var b = uriBuilder
                            .path(ApiPaths.GLOSSARY_SERVICE_PATH)
                            .path("/file")
                            .queryParam("fileType", fileType)
                            .queryParam("qualifiedName", qualifiedName);
                    if (transLang != null && !transLang.isEmpty()) {
                        b.queryParam("locales", Joiner.on(",").join(transLang));
                    }
                    return b.build();
                })
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .retrieve()
                .toEntity(byte[].class);
    }

    public void delete(String id, String qualifiedName) {
        factory.getSpringRestClient().delete()
                .uri(uriBuilder -> uriBuilder
                        .path(ApiPaths.GLOSSARY_SERVICE_PATH)
                        .path("/entries/" + id)
                        .queryParam("qualifiedName", qualifiedName)
                        .build())
                .retrieve()
                .toBodilessEntity();
    }

    public int deleteAll(String qualifiedName) {
        Integer result = factory.getSpringRestClient().delete()
                .uri(uriBuilder -> uriBuilder
                        .path(ApiPaths.GLOSSARY_SERVICE_PATH)
                        .queryParam("qualifiedName", qualifiedName)
                        .build())
                .retrieve()
                .body(Integer.class);
        return result == null ? 0 : result;
    }

    public String getProjectQualifiedName(String projectSlug) {
        QualifiedName qn = factory.getSpringRestClient().get()
                .uri("projects/p/{slug}/glossary/qualifiedName", projectSlug)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(QualifiedName.class);
        return qn == null ? null : qn.getName();
    }

    public String getGlobalQualifiedName() {
        QualifiedName qn = factory.getSpringRestClient().get()
                .uri(ApiPaths.GLOSSARY_SERVICE_PATH + "/qualifiedName")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(QualifiedName.class);
        return qn == null ? null : qn.getName();
    }

}
