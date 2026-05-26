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

import static org.zanata.common.ProjectType.fileProjectSourceDocTypes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.zanata.adapter.po.PoWriter2;
import org.zanata.common.ContentState;
import org.zanata.common.DocumentType;
import org.zanata.common.FileTypeInfo;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.ChunkUploadResponse;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/file")
public class MockFileResource {

    @Deprecated
    @GetMapping(value = "/accepted_types",
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> acceptedFileTypes() {
        StringSet extensions = new StringSet("");
        for (DocumentType docType : ProjectType
                .getSupportedSourceFileTypes(ProjectType.File)) {
            extensions.addAll(docType.getSourceExtensions());
        }
        for (DocumentType docType : ProjectType
                .getSupportedSourceFileTypes(ProjectType.Gettext)) {
            extensions.addAll(docType.getSourceExtensions());
        }
        return ResponseEntity.ok(extensions.toString());
    }

    @Deprecated
    @GetMapping(value = "/accepted_document_types",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<DocumentType>> acceptedFileTypeList() {
        return ResponseEntity.ok(fileProjectSourceDocTypes());
    }

    @GetMapping(value = "/file_type_info",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<FileTypeInfo>> fileTypeInfoList() {
        List<FileTypeInfo> fileTypeInfoList = fileProjectSourceDocTypes()
                .stream().map(DocumentType::toFileTypeInfo)
                .collect(Collectors.toList());
        return ResponseEntity.ok(fileTypeInfoList);
    }

    @PostMapping(value = "/source/{projectSlug}/{iterationSlug}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChunkUploadResponse> uploadSourceFile(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam("docId") String docId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "first", required = false) String first,
            @RequestParam(value = "last", required = false) String last,
            @RequestParam(value = "hash", required = false) String hash,
            @RequestParam(value = "size", required = false) String size) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ChunkUploadResponse(1L, 1, false,
                        "Upload of new source document successful."));
    }

    @PostMapping(value = "/translation/{projectSlug}/{iterationSlug}/{locale}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ChunkUploadResponse> uploadTranslationFile(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") String localeId,
            @RequestParam("docId") String docId,
            @RequestParam(value = "merge", required = false) String merge,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "first", required = false) String first,
            @RequestParam(value = "last", required = false) String last,
            @RequestParam(value = "hash", required = false) String hash,
            @RequestParam(value = "size", required = false) String size) {
        return ResponseEntity.ok(
                new ChunkUploadResponse(1L, 1, false,
                        "Translations uploaded successfully"));
    }

    @GetMapping("/source/{projectSlug}/{iterationSlug}/{fileType}")
    public ResponseEntity<byte[]> downloadSourceFile(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("fileType") String fileType,
            @RequestParam("docId") String docId) {
        byte[] body;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PoWriter2 writer = new PoWriter2.Builder().create();
            Resource doc = sampleResource(docId);
            writer.writePot(out, "UTF-8", doc);
            body = out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + docId + ".pot\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    private static Resource sampleResource(String docId) {
        Resource doc = new Resource(docId);
        doc.getTextFlows().add(new TextFlow("hello", LocaleId.EN_US,
                "hello world"));
        return doc;
    }

    @GetMapping("/translation/{projectSlug}/{iterationSlug}/{locale}/{fileType}")
    public ResponseEntity<byte[]> downloadTranslationFile(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") String locale,
            @PathVariable("fileType") String fileExtension,
            @RequestParam("docId") String docId,
            @RequestParam(value = "approvedOnly",
                    defaultValue = "false") boolean approvedOnly) {
        byte[] body;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PoWriter2 writer = new PoWriter2.Builder()
                    .approvedOnly(approvedOnly).create();
            writer.writePo(out, "UTF-8", sampleResource(docId),
                    sampleTransResource());
            body = out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + docId + ".po\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }

    private static TranslationsResource sampleTransResource() {
        TranslationsResource resource = new TranslationsResource();
        resource.getExtensions(true);
        TextFlowTarget hello = new TextFlowTarget("hello");
        hello.getExtensions(true);
        hello.setState(ContentState.Translated);
        hello.setContents("hola mundo");
        resource.getTextFlowTargets().add(hello);
        return resource;
    }

    @GetMapping("/download/{downloadId}")
    public ResponseEntity<byte[]> download(
            @PathVariable("downloadId") String downloadId) {
        byte[] body;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PoWriter2 writer = new PoWriter2.Builder().create();
            writer.writePo(out, "UTF-8", sampleResource(downloadId),
                    sampleTransResource());
            body = out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + downloadId + ".po\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(body);
    }
}
