package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.Project;

class ProjectClientIT extends AbstractRestClientIT {

    @Test
    void testGet() throws Exception {
        fixtures.ensureProject("about-fedora", "master");
        Project project = factory().getProjectClient("about-fedora").get();
        assertThat(project.getId()).isEqualTo("about-fedora");
    }

    @Test
    void testPut() throws Exception {
        var response = factory().getProjectClient("a")
                .put(new Project("a", "b", "file"));
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
