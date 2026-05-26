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

import java.util.Collection;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}")
public class MockSourceDocResource {

    @RequestMapping(value = "/r", method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/r")
    public ResponseEntity<Collection<ResourceMeta>> get(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        MockResourceUtil.validateExtensions(extensions);
        Collection<ResourceMeta> samples =
                new ResourceMeta("about-fedora").createSamples();
        return ResponseEntity.ok(samples);
    }

    @PostMapping("/r")
    public ResponseEntity<Void> post(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody Resource resource,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "copyTrans",
                    defaultValue = "true") boolean copyTrans) {
        return MockResourceUtil.notUsedByClient();
    }

    @Deprecated
    @GetMapping("/r/{id}")
    public ResponseEntity<Resource> getResource(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        return getResourceWithDocId(projectSlug, iterationSlug, idNoSlash,
                extensions);
    }

    @GetMapping("/resource")
    public ResponseEntity<Resource> getResourceWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        MockResourceUtil.validateExtensions(extensions);
        return ResponseEntity.ok(new Resource(docId));
    }

    @Deprecated
    @PutMapping("/r/{id}")
    public ResponseEntity<String> putResource(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @RequestBody Resource resource,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "copyTrans",
                    defaultValue = "true") boolean copyTrans) {
        return putResourceWithDocId(projectSlug, iterationSlug, resource,
                idNoSlash, extensions, copyTrans);
    }

    @PutMapping("/resource")
    public ResponseEntity<String> putResourceWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody Resource resource,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "copyTrans",
                    defaultValue = "true") boolean copytrans) {
        MockResourceUtil.validateExtensions(extensions);
        return ResponseEntity.ok(resource.getName());
    }

    @Deprecated
    @DeleteMapping("/r/{id}")
    public ResponseEntity<Void> deleteResource(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash) {
        return deleteResourceWithDocId(projectSlug, iterationSlug, idNoSlash);
    }

    @DeleteMapping("/resource")
    public ResponseEntity<Void> deleteResourceWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "docId", defaultValue = "") String docId) {
        return ResponseEntity.ok().build();
    }

    @Deprecated
    @GetMapping("/r/{id}/meta")
    public ResponseEntity<ResourceMeta> getResourceMeta(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        return getResourceMetaWithDocId(projectSlug, iterationSlug, idNoSlash,
                extensions);
    }

    @GetMapping("/resource/meta")
    public ResponseEntity<ResourceMeta> getResourceMetaWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        return MockResourceUtil.notUsedByClient();
    }

    @Deprecated
    @PutMapping("/r/{id}/meta")
    public ResponseEntity<Void> putResourceMeta(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @RequestBody ResourceMeta resourceMeta,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        return putResourceMetaWithDocId(projectSlug, iterationSlug,
                resourceMeta, idNoSlash, extensions);
    }

    @PutMapping("/resource/meta")
    public ResponseEntity<Void> putResourceMetaWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody ResourceMeta messageBody,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions) {
        return MockResourceUtil.notUsedByClient();
    }
}
