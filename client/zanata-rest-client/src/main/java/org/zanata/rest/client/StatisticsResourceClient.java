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

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.zanata.rest.dto.stats.ContainerTranslationStatistics;
import org.zanata.rest.dto.stats.contribution.ContributionStatistics;

public class StatisticsResourceClient {
    private final RestClientFactory factory;

    StatisticsResourceClient(RestClientFactory factory) {
        this.factory = factory;
    }

    public ContainerTranslationStatistics getStatistics(String projectSlug,
            String iterationSlug, boolean includeDetails,
            boolean includeWordStats, String[] locales) {
        return rest().get()
                .uri(uri -> uri.path("stats/proj/{proj}/iter/{iter}")
                        .queryParam("detail", includeDetails)
                        .queryParam("word", includeWordStats)
                        .queryParam("locale", (Object[]) locales)
                        .build(projectSlug, iterationSlug))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ContainerTranslationStatistics.class);
    }

    @Deprecated
    public ContainerTranslationStatistics getStatistics(String projectSlug,
            String iterationSlug, String docId, boolean includeWordStats,
            String[] locales) {
        return getStatisticsWithDocId(projectSlug, iterationSlug, docId,
                includeWordStats, locales);
    }

    public ContainerTranslationStatistics getStatisticsWithDocId(
            String projectSlug, String iterationSlug, String docId,
            boolean includeWordStats, String[] locales) {
        try {
            return rest().get()
                    .uri(uri -> uri.path("stats/proj/{proj}/iter/{iter}/doc")
                            .queryParam("docId", docId)
                            .queryParam("word", includeWordStats)
                            .queryParam("locale", (Object[]) locales)
                            .build(projectSlug, iterationSlug))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContainerTranslationStatistics.class);
        } catch (HttpClientErrorException.NotFound e) {
            return rest().get()
                    .uri(uri -> uri.path(
                                    "stats/proj/{proj}/iter/{iter}/doc/{docId}")
                            .queryParam("word", includeWordStats)
                            .queryParam("locale", (Object[]) locales)
                            .build(projectSlug, iterationSlug, docId))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ContainerTranslationStatistics.class);
        }
    }

    public ContributionStatistics getContributionStatistics(String projectSlug,
            String versionSlug, String username, String dateRange,
            boolean includeAutomatedEntry) {
        return rest().get()
                .uri(uri -> uri.path("stats/project/{proj}/version/{ver}/"
                                + "contributor/{user}/{range}")
                        .queryParam("includeAutomatedEntry", includeAutomatedEntry)
                        .build(projectSlug, versionSlug, username, dateRange))
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ContributionStatistics.class);
    }

    private RestClient rest() {
        return factory.getSpringRestClient();
    }
}
