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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.rest.dto.CopyTransStatus;

/**
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/copytrans")
public class MockCopyTransResource {

    @PostMapping("/proj/{projectSlug}/iter/{iterationSlug}/doc/{docId}")
    public CopyTransStatus startCopyTrans(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("docId") String docId) {
        CopyTransStatus copyTransStatus = new CopyTransStatus();
        copyTransStatus.setInProgress(true);
        copyTransStatus.setPercentageComplete(50);
        return copyTransStatus;
    }

    @GetMapping("/proj/{projectSlug}/iter/{iterationSlug}/doc/{docId}")
    public CopyTransStatus getCopyTransStatus(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("docId") String docId) {
        CopyTransStatus copyTransStatus = new CopyTransStatus();
        copyTransStatus.setInProgress(false);
        copyTransStatus.setPercentageComplete(100);
        return copyTransStatus;
    }
}
