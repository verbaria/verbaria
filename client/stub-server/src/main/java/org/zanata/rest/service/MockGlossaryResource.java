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

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.rest.dto.GlossaryEntry;
import org.zanata.rest.dto.GlossaryResults;

/**
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/glossary")
public class MockGlossaryResource {

    /** Maximum result for per page. */
    public static final int MAX_PAGE_SIZE = 1000;

    /** Qualified name for Global/default glossary */
    public static final String GLOBAL_QUALIFIED_NAME = "global/default";

    @GetMapping("/info")
    public ResponseEntity<String> getInfo(
            @RequestParam(value = "qualifiedName",
                    defaultValue = GLOBAL_QUALIFIED_NAME) String qualifiedName) {
        return ResponseEntity.ok(GLOBAL_QUALIFIED_NAME);
    }

    @PostMapping("/entries")
    public ResponseEntity<GlossaryResults> post(
            @RequestBody List<GlossaryEntry> glossaryEntries,
            @RequestParam(value = "locale", required = false) String locale,
            @RequestParam(value = "qualifiedName",
                    defaultValue = GLOBAL_QUALIFIED_NAME) String qualifiedName) {
        GlossaryResults results = new GlossaryResults();
        results.setGlossaryEntries(glossaryEntries);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/qualifiedName")
    public ResponseEntity<Void> getQualifiedName() {
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/entries/{id}")
    public ResponseEntity<Void> deleteEntry(
            @org.springframework.web.bind.annotation.PathVariable("id") Long id,
            @RequestParam(value = "qualifiedName",
                    defaultValue = GLOBAL_QUALIFIED_NAME) String qualifiedName) {
        return MockResourceUtil.notUsedByClient();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAllEntries(
            @RequestParam(value = "qualifiedName",
                    defaultValue = GLOBAL_QUALIFIED_NAME) String qualifiedName) {
        return ResponseEntity.ok().build();
    }
}
