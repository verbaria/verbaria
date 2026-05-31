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

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.zanata.rest.dto.ProjectIteration;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}")
public class MockProjectIterationResource {

    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> head(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug) {
        return MockResourceUtil.notUsedByClient();
    }

    @GetMapping
    public ResponseEntity<ProjectIteration> get(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug) {
        return ResponseEntity.ok(new ProjectIteration("master"));
    }

    @PutMapping
    public ResponseEntity<Void> put(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody ProjectIteration project) {
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest().build()
                        .toUri())
                .build();
    }

    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> sampleConfiguration(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug) {
        String baseUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .build().toUriString() + "/";
        String config = "{\n"
                + "  \"url\": \"" + baseUri + "\",\n"
                + "  \"project\": \"about-fedora\",\n"
                + "  \"projectVersion\": \"master\"\n"
                + "}";
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                .body(config);
    }
}
