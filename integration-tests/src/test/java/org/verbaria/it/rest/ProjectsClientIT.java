package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.Project;

class ProjectsClientIT extends AbstractRestClientIT {

    @Test
    void canGetProjects() throws Exception {
        fixtures.ensureProject("about-fedora", "master");
        Project[] projects = factory().getProjectsClient().getProjects();
        assertThat(projects).anyMatch(p -> "about-fedora".equals(p.getId()));
    }
}
