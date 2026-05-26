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

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.zanata.common.DocumentType;
import org.zanata.common.FileTypeInfo;
import org.zanata.rest.DocumentFileUploadForm;
import org.zanata.rest.dto.ChunkUploadResponse;
import org.zanata.rest.service.FileResource;

public class FileResourceClient {
    private final RestClientFactory factory;

    FileResourceClient(RestClientFactory restClientFactory) {
        this.factory = restClientFactory;
    }

    @Deprecated
    public List<DocumentType> acceptedFileTypes() {
        List<DocumentType> types = rest().get()
                .uri(FileResource.SERVICE_PATH
                        + FileResource.ACCEPTED_TYPE_LIST_RESOURCE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<DocumentType>>() {});
        return types == null ? List.of() : types;
    }

    public List<FileTypeInfo> fileTypeInfoList() {
        List<FileTypeInfo> types = rest().get()
                .uri(FileResource.SERVICE_PATH
                        + FileResource.FILE_TYPE_INFO_RESOURCE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<FileTypeInfo>>() {});
        return types == null ? List.of() : types;
    }

    public ChunkUploadResponse uploadSourceFile(String projectSlug,
            String iterationSlug, String docId,
            DocumentFileUploadForm form) {
        return rest().post()
                .uri(uri -> uri.path("file/source/{proj}/{iter}")
                        .queryParam("docId", docId)
                        .build(projectSlug, iterationSlug))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(toMultipart(form))
                .retrieve()
                .body(ChunkUploadResponse.class);
    }

    public ChunkUploadResponse uploadTranslationFile(String projectSlug,
            String iterationSlug, String locale, String docId,
            String mergeType, DocumentFileUploadForm form) {
        return rest().post()
                .uri(uri -> uri.path(FileResource.SERVICE_PATH
                                + "/translation/{proj}/{iter}/{loc}")
                        .queryParam("docId", docId)
                        .queryParam("merge", mergeType)
                        .build(projectSlug, iterationSlug, locale))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON)
                .body(toMultipart(form))
                .retrieve()
                .body(ChunkUploadResponse.class);
    }

    public ResponseEntity<byte[]> downloadSourceFile(String projectSlug,
            String iterationSlug, String fileType, String docId) {
        return rest().get()
                .uri(uri -> uri.path(FileResource.SERVICE_PATH
                                + "/source/{proj}/{iter}/{ft}")
                        .queryParam("docId", docId)
                        .build(projectSlug, iterationSlug, fileType))
                .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                .retrieve()
                .toEntity(byte[].class);
    }

    public ResponseEntity<byte[]> downloadTranslationFile(String projectSlug,
            String iterationSlug, String locale, String fileExtension,
            String docId) {
        return downloadTranslationFile(projectSlug, iterationSlug, locale,
                fileExtension, docId, false);
    }

    public ResponseEntity<byte[]> downloadTranslationFile(String projectSlug,
            String iterationSlug, String locale, String fileExtension,
            String docId, boolean approvedOnly) {
        return rest().get()
                .uri(uri -> uri.path(FileResource.SERVICE_PATH
                                + "/translation/{proj}/{iter}/{loc}/{ext}")
                        .queryParam("docId", docId)
                        .queryParam("approvedOnly", approvedOnly)
                        .build(projectSlug, iterationSlug, locale,
                                fileExtension))
                .accept(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL)
                .retrieve()
                .toEntity(byte[].class);
    }

    private static Object toMultipart(DocumentFileUploadForm form) {
        MultipartBodyBuilder b = new MultipartBodyBuilder();
        if (form.getFileStream() != null) {
            // Wrap as ByteArrayResource so RestClient streams it as a file part.
            try {
                byte[] bytes = form.getFileStream().readAllBytes();
                b.part("file", new org.springframework.core.io.ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return "file";
                    }
                }, MediaType.APPLICATION_OCTET_STREAM);
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (form.getFileType() != null) b.part("type", form.getFileType());
        if (form.getUploadId() != null)
            b.part("uploadId", form.getUploadId().toString());
        if (form.getFirst() != null)
            b.part("first", form.getFirst().toString());
        if (form.getLast() != null) b.part("last", form.getLast().toString());
        if (form.getHash() != null) b.part("hash", form.getHash());
        if (form.getSize() != null) b.part("size", form.getSize().toString());
        if (form.getAdapterParams() != null)
            b.part("adapterParams", form.getAdapterParams());
        return b.build();
    }

    private RestClient rest() {
        return factory.getSpringRestClient();
    }
}
