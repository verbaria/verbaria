package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.ProjectIteration;

class ProjectIterationClientIT extends AbstractRestClientIT {

    @Test
    void testGet() throws Exception {
        fixtures.ensureProject("about-fedora", "master");
        ProjectIteration iteration = factory()
                .getProjectIterationClient("about-fedora", "master").get();
        assertThat(iteration.getId()).isEqualTo("master");
    }

    @Test
    void testPut() throws Exception {
        fixtures.ensureProject("about-fedora", "master");
        var response = factory()
                .getProjectIterationClient("about-fedora", "1.1")
                .put(new ProjectIteration("1.1"));
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void testSampleConfig() throws Exception {
        fixtures.ensureProject("about-fedora", "master");
        String config = factory()
                .getProjectIterationClient("about-fedora", "master")
                .sampleConfiguration();
        assertThat(config).contains("project");
    }
}
