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

import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}")
public class MockTranslatedDocResource {

    @Deprecated
    @GetMapping("/r/{id}/translations/{locale}")
    public ResponseEntity<TranslationsResource> getTranslations(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @PathVariable("locale") LocaleId locale,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "skeletons",
                    defaultValue = "false") boolean createSkeletons,
            @RequestParam(value = "markTranslatedAsApproved",
                    defaultValue = "true") boolean markTranslatedAsApproved,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                    required = false) String eTag) {
        return getTranslationsWithDocId(projectSlug, iterationSlug, locale,
                idNoSlash, extensions, createSkeletons,
                markTranslatedAsApproved, eTag);
    }

    @GetMapping("/resource/translations/{locale}")
    public ResponseEntity<TranslationsResource> getTranslationsWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") LocaleId locale,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "skeletons",
                    defaultValue = "false") boolean createSkeletons,
            @RequestParam(value = "markTranslatedAsApproved",
                    defaultValue = "true") boolean markTranslatedAsApproved,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH,
                    required = false) String eTag) {
        MockResourceUtil.validateExtensions(extensions);
        TranslationsResource transResource = new TranslationsResource();
        transResource.getTextFlowTargets().add(new TextFlowTarget(docId));
        return ResponseEntity.ok(transResource);
    }

    @Deprecated
    @DeleteMapping("/r/{id}/translations/{locale}")
    public ResponseEntity<Void> deleteTranslations(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @PathVariable("locale") LocaleId locale) {
        return deleteTranslationsWithDocId(projectSlug, iterationSlug, locale,
                idNoSlash);
    }

    @DeleteMapping("/resource/translations/{locale}")
    public ResponseEntity<Void> deleteTranslationsWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") LocaleId locale,
            @RequestParam(value = "docId", defaultValue = "") String docId) {
        return MockResourceUtil.notUsedByClient();
    }

    @Deprecated
    @PutMapping("/r/{id}/translations/{locale}")
    public ResponseEntity<Void> putTranslations(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("id") String idNoSlash,
            @PathVariable("locale") LocaleId locale,
            @RequestBody TranslationsResource messageBody,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "merge",
                    defaultValue = "auto") String merge) {
        return putTranslationsWithDocId(projectSlug, iterationSlug, locale,
                messageBody, idNoSlash, extensions, merge);
    }

    @PutMapping("/resource/translations/{locale}")
    public ResponseEntity<Void> putTranslationsWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") LocaleId locale,
            @RequestBody TranslationsResource messageBody,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "merge",
                    defaultValue = "auto") String merge) {
        MockResourceUtil.validateExtensions(extensions);
        return ResponseEntity.ok().build();
    }
}
