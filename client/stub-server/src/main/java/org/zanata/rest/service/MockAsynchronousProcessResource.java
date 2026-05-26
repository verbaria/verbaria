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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.ProcessStatus;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/async")
public class MockAsynchronousProcessResource {

    @PutMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}/r/{id}")
    @Deprecated
    public ProcessStatus startSourceDocCreationOrUpdate(
            @PathVariable("id") String idNoSlash,
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody Resource resource,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "copyTrans",
                    defaultValue = "true") boolean copytrans) {
        return startSourceDocCreationOrUpdateWithDocId(projectSlug,
                iterationSlug, resource, extensions, idNoSlash);
    }

    @PutMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}/resource")
    public ProcessStatus startSourceDocCreationOrUpdateWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestBody Resource resource,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "docId", defaultValue = "") String docId) {
        ProcessStatus processStatus = new ProcessStatus();
        processStatus.setStatusCode(ProcessStatus.ProcessStatusCode.Running);
        processStatus.setPercentageComplete(50);
        processStatus.setUrl("process1");
        return processStatus;
    }

    @PutMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}/r/{id}/translations/{locale}")
    @Deprecated
    public ProcessStatus startTranslatedDocCreationOrUpdate(
            @PathVariable("id") String idNoSlash,
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") LocaleId locale,
            @RequestBody TranslationsResource translatedDoc,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "merge", required = false) String merge,
            @RequestParam(value = "assignCreditToUploader",
                    defaultValue = "false") boolean myTrans) {
        return startTranslatedDocCreationOrUpdateWithDocId(projectSlug,
                iterationSlug, locale, translatedDoc, idNoSlash, extensions,
                merge, myTrans);
    }

    @PutMapping("/projects/p/{projectSlug}/iterations/i/{iterationSlug}/resource/translations/{locale}")
    public ProcessStatus startTranslatedDocCreationOrUpdateWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("locale") LocaleId locale,
            @RequestBody TranslationsResource translatedDoc,
            @RequestParam(value = "docId", defaultValue = "") String docId,
            @RequestParam(value = "ext", required = false) Set<String> extensions,
            @RequestParam(value = "merge", required = false) String merge,
            @RequestParam(value = "assignCreditToUploader",
                    defaultValue = "false") boolean assignCreditToUploader) {
        ProcessStatus processStatus = new ProcessStatus();
        processStatus.setStatusCode(ProcessStatus.ProcessStatusCode.Running);
        processStatus.setPercentageComplete(50);
        processStatus.setUrl("process2");
        return processStatus;
    }

    @GetMapping("/{processId}")
    public ProcessStatus getProcessStatus(
            @PathVariable("processId") String processId) {
        ProcessStatus processStatus = new ProcessStatus();
        processStatus.setStatusCode(ProcessStatus.ProcessStatusCode.Finished);
        processStatus.setPercentageComplete(100);
        processStatus.setUrl(processId);
        return processStatus;
    }
}
