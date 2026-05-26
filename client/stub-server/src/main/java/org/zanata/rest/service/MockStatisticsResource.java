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

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.common.TransUnitCount;
import org.zanata.rest.dto.stats.ContainerTranslationStatistics;
import org.zanata.rest.dto.stats.TranslationStatistics;
import org.zanata.rest.dto.stats.contribution.BaseContributionStatistic;
import org.zanata.rest.dto.stats.contribution.ContributionStatistics;
import org.zanata.rest.dto.stats.contribution.LocaleStatistics;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@RestController
@RequestMapping("/stats")
public class MockStatisticsResource {

    @GetMapping("/proj/{projectSlug}/iter/{iterationSlug}")
    public ContainerTranslationStatistics getStatistics(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "detail",
                    defaultValue = "false") boolean includeDetails,
            @RequestParam(value = "word",
                    defaultValue = "false") boolean includeWordStats,
            @RequestParam(value = "locale", required = false) String[] locales) {
        return generateStatistics(iterationSlug, locales);
    }

    private ContainerTranslationStatistics generateStatistics(
            String id, String[] locales) {
        ContainerTranslationStatistics stats =
                new ContainerTranslationStatistics();
        stats.setId(id);
        if (locales != null) {
            for (String locale : locales) {
                stats.addStats(new TranslationStatistics(new TransUnitCount(
                        100, 0, 0, 100, 0), locale.trim()));
            }
        }
        return stats;
    }

    @Deprecated
    @GetMapping("/proj/{projectSlug}/iter/{iterationSlug}/doc/{docId}")
    public ContainerTranslationStatistics getStatisticsByDocPath(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @PathVariable("docId") String docId,
            @RequestParam(value = "word",
                    defaultValue = "false") boolean includeWordStats,
            @RequestParam(value = "locale", required = false) String[] locales) {
        return getStatisticsWithDocId(projectSlug, iterationSlug, docId,
                includeWordStats, locales);
    }

    @GetMapping("/proj/{projectSlug}/iter/{iterationSlug}/doc")
    public ContainerTranslationStatistics getStatisticsWithDocId(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("iterationSlug") String iterationSlug,
            @RequestParam(value = "docId", required = false) String docId,
            @RequestParam(value = "word",
                    defaultValue = "false") boolean includeWordStats,
            @RequestParam(value = "locale", required = false) String[] locales) {
        return generateStatistics(docId, locales);
    }

    @GetMapping("/project/{projectSlug}/version/{versionSlug}/contributor/{username}/{dateRange}")
    public ContributionStatistics getContributionStatistics(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug,
            @PathVariable("username") String username,
            @PathVariable("dateRange") String dateRange,
            @RequestParam(value = "includeAutomatedEntry",
                    defaultValue = "false") boolean includeAutomatedEntry) {
        BaseContributionStatistic transStats =
                new BaseContributionStatistic(100, 90, 100, 0);
        BaseContributionStatistic reviewStats =
                new BaseContributionStatistic(100, 0, 0, 10);

        LocaleStatistics localeStatistics =
                new LocaleStatistics(new LocaleId("zh"), transStats, reviewStats);

        List<LocaleStatistics> localeStatisticsList = new ArrayList<>();
        localeStatisticsList.add(localeStatistics);

        return new ContributionStatistics(username, localeStatisticsList);
    }

    @GetMapping("/project/{projectSlug}/version/{versionSlug}/{dateRangeParam}")
    public ResponseEntity<Void> getProjectStatisticsMatrix(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug,
            @PathVariable("dateRangeParam") String dateRangeParam,
            @RequestParam(value = "timeZoneID",
                    required = false) String timeZoneID) {
        return MockResourceUtil.notUsedByClient();
    }
}
