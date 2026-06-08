package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.zanata.rest.client.RestClientFactory;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.ProjectIteration;
import org.zanata.rest.dto.VersionInfo;

/**
 * Exercises the {@code zanata-rest-client} classes against the real headless
 * server (the same REST contract the CLI uses), replacing the old in-memory
 * {@code stub-server}. There is only one API now.
 */
@SpringBootTest(classes = ItApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestClientIT {

    private static final String USER = "admin";
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Autowired
    ItFixtures fixtures;

    private RestClientFactory factory() throws Exception {
        // Default url prefix is "rest/" — exactly where the headless server
        // mounts the CLI bridge, so no override is needed (unlike stub-server).
        return new RestClientFactory(
                new URI("http://localhost:" + port + "/"), USER, API_KEY,
                new VersionInfo("5.2.0", "unknown", "unknown"), true, true);
    }

    @Test
    void projectsClientListsProjects() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("about-fedora", "master");

        Project[] projects = factory().getProjectsClient().getProjects();

        assertThat(projects)
                .anyMatch(p -> "about-fedora".equals(p.getId()));
    }

    @Test
    void serverVersionIsReported() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        VersionInfo info = factory().getServerVersionInfo();
        assertThat(info.getVersionNo()).isNotBlank();
    }

    @Test
    void projectClientGetsAndPutsProject() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("about-fedora", "master");

        Project project = factory().getProjectClient("about-fedora").get();
        assertThat(project.getId()).isEqualTo("about-fedora");
    }

    @Test
    void projectIterationClientGetsIterationAndConfig() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("about-fedora", "master");

        ProjectIteration iteration = factory()
                .getProjectIterationClient("about-fedora", "master").get();
        assertThat(iteration.getId()).isEqualTo("master");

        String config = factory()
                .getProjectIterationClient("about-fedora", "master")
                .sampleConfiguration();
        assertThat(config).contains("project");
    }
}
