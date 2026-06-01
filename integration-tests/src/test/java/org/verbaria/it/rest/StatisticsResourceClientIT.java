package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import org.zanata.rest.dto.stats.ContainerTranslationStatistics;

class StatisticsResourceClientIT extends AbstractRestClientIT {

    @Test
    void testGetIterationStatistics() throws Exception {
        fixtures.ensureProject("rcstats", "master");
        ContainerTranslationStatistics statistics = factory()
                .getStatisticsClient()
                .getStatistics("rcstats", "master", true, true, new String[0]);
        // The headless server identifies iteration stats as "<proj>:<version>".
        assertThat(statistics.getId()).isEqualTo("rcstats:master");
    }

    @Test
    void testGetDocStatistics() throws Exception {
        fixtures.ensureProject("rcstats", "master");
        // Document-level statistics are not part of the one-API CLI bridge.
        assertThatThrownBy(() -> factory().getStatisticsClient()
                .getStatistics("rcstats", "master", "About-Fedora", true,
                        new String[0]))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testGetDocStatisticsWithDocId() throws Exception {
        fixtures.ensureProject("rcstats", "master");
        assertThatThrownBy(() -> factory().getStatisticsClient()
                .getStatisticsWithDocId("rcstats", "master", "About-Fedora",
                        true, new String[0]))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testGetContributorStatistics() throws Exception {
        fixtures.ensureProject("rcstats", "master");
        // Contributor statistics are not part of the one-API CLI bridge.
        assertThatThrownBy(() -> factory().getStatisticsClient()
                .getContributionStatistics("rcstats", "master", "admin",
                        "2020-01-01..2020-12-31", false))
                .isInstanceOf(RestClientResponseException.class);
    }
}
