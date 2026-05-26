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

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.zanata.common.LocaleId;
import org.zanata.rest.RestUtil;
import org.zanata.rest.dto.ProcessStatus;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

public class AsyncProcessClient {
    private static final String BASE = "/async/projects/p/{proj}/iterations/i/{iter}";

    private final RestClientFactory factory;

    AsyncProcessClient(RestClientFactory factory) {
        this.factory = factory;
    }

    @Deprecated

    public ProcessStatus startSourceDocCreation(String idNoSlash,
            String projectSlug, String iterationSlug, Resource resource,
            Set<String> extensions, boolean copytrans) {
        throw new UnsupportedOperationException(
                "Not supported. Use startSourceDocCreationOrUpdate instead.");
    }


    @Deprecated
    public ProcessStatus startSourceDocCreationOrUpdate(String idNoSlash,
            String projectSlug, String iterationSlug, Resource resource,
            Set<String> extensions, boolean copytrans) {
        Object[] extArr = extensions.toArray();
        return rest().put()
                .uri(uri -> uri.path(BASE + "/r/{docId}")
                        .queryParam("ext", extArr)
                        .queryParam("copyTrans", copytrans)
                        .build(projectSlug, iterationSlug, idNoSlash))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
                .retrieve()
                .body(ProcessStatus.class);
    }


    public ProcessStatus startSourceDocCreationOrUpdateWithDocId(
            String projectSlug, String iterationSlug, Resource resource,
            Set<String> extensions, String docId) {
        Object[] extArr = extensions.toArray();
        try {
            return rest().put()
                    .uri(uri -> uri.path(BASE + "/resource")
                            .queryParam("docId", docId)
                            .queryParam("ext", extArr)
                            .build(projectSlug, iterationSlug))
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resource)
                    .retrieve()
                    .body(ProcessStatus.class);
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            return startSourceDocCreationOrUpdate(idNoSlash, projectSlug,
                    iterationSlug, resource, extensions, false);
        }
    }


    @Deprecated
    public ProcessStatus startTranslatedDocCreationOrUpdate(String idNoSlash,
            String projectSlug, String iterationSlug, LocaleId locale,
            TranslationsResource translatedDoc, Set<String> extensions,
            String merge, boolean myTrans) {
        Object[] extArr = extensions.toArray();
        return rest().put()
                .uri(uri -> uri.path(
                                BASE + "/r/{docId}/translations/{locale}")
                        .queryParam("ext", extArr)
                        .queryParam("merge", merge)
                        .queryParam("assignCreditToUploader", myTrans)
                        .build(projectSlug, iterationSlug, idNoSlash,
                                locale.toString()))
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(translatedDoc)
                .retrieve()
                .body(ProcessStatus.class);
    }


    public ProcessStatus startTranslatedDocCreationOrUpdateWithDocId(
            String projectSlug, String iterationSlug, LocaleId locale,
            TranslationsResource translatedDoc, String docId,
            Set<String> extensions, String merge,
            boolean assignCreditToUploader) {
        Object[] extArr = extensions.toArray();
        try {
            return rest().put()
                    .uri(uri -> uri.path(
                                    BASE + "/resource/translations/{locale}")
                            .queryParam("docId", docId)
                            .queryParam("ext", extArr)
                            .queryParam("merge", merge)
                            .queryParam("assignCreditToUploader",
                                    assignCreditToUploader)
                            .build(projectSlug, iterationSlug,
                                    locale.toString()))
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(translatedDoc)
                    .retrieve()
                    .body(ProcessStatus.class);
        } catch (HttpClientErrorException.NotFound e) {
            String idNoSlash = RestUtil.convertToDocumentURIId(docId);
            return startTranslatedDocCreationOrUpdate(idNoSlash, projectSlug,
                    iterationSlug, locale, translatedDoc, extensions, merge,
                    assignCreditToUploader);
        }
    }


    public ProcessStatus getProcessStatus(String processId) {
        return rest().get()
                .uri("/async/{id}", processId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ProcessStatus.class);
    }

    private RestClient rest() {
        return factory.getSpringRestClient();
    }
}
