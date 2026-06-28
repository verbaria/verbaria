package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.verbaria.server.headless.repository.ProjectIterationRepository;

/**
 * A child project inherits its parent's languages and project type, and a child
 * gets a mirror of the parent's versions so push/pull never 404s.
 */
class ParentProjectInheritanceIT extends AbstractPushPullIT {

    @Autowired
    ProjectIterationRepository iterationRepository;

    @Test
    void findProjectLocalesResolvesParentWithoutOpenSession() throws Exception {
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru-RU");
        fixtures.ensureLocale("zh-CN");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("sescparent", VERSION);
        fixtures.setProjectLocales("sescparent", "ru-RU", "zh-CN");
        fixtures.ensureProjectNoVersion("sescchild", "properties");
        fixtures.linkParent("sescchild", "sescparent");

        Long iterId = iterationRepository
                .findByProjectAndSlug("sescchild", VERSION).orElseThrow().getId();
        // Called with no ambient transaction/session (the stats-cache path) —
        // must resolve the parent's locales without a LazyInitializationException.
        var locales = iterationRepository.findProjectLocales(iterId).orElseThrow();
        assertThat(locales.stream().map(l -> l.getLocaleId().getId()).toList())
                .as("child inherits the parent's locales even with no open session")
                .containsExactlyInAnyOrder("ru-RU", "zh-CN");
    }

    @Test
    void childInheritsParentLanguagesAndMirrorsVersions() throws Exception {
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru-RU");
        fixtures.ensureLocale("zh-CN");
        fixtures.ensureAdmin(USER, API_KEY);

        fixtures.ensureProject("ppconsulo", VERSION);
        fixtures.setProjectLocales("ppconsulo", "ru-RU", "zh-CN");

        // Child has no version of its own yet — linking must mirror it.
        fixtures.ensureProjectNoVersion("ppconsulojava", "properties");
        fixtures.linkParent("ppconsulojava", "ppconsulo");

        assertThat(fixtures.hasVersion("ppconsulojava", VERSION))
                .as("linking must mirror the parent's version onto the child")
                .isTrue();

        assertThat(localeIds("ppconsulojava", VERSION))
                .as("child with no override inherits the parent's languages")
                .containsExactlyInAnyOrder("ru-RU", "zh-CN");

        // A new parent version propagates to the child.
        fixtures.addVersionAndPropagate("ppconsulo", "1.0");
        assertThat(fixtures.hasVersion("ppconsulojava", "1.0"))
                .as("a new parent version must propagate to children")
                .isTrue();
    }

    @Test
    void standaloneChildOverrideWinsOverParent() throws Exception {
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru-RU");
        fixtures.ensureLocale("de-DE");
        fixtures.ensureAdmin(USER, API_KEY);

        fixtures.ensureProject("ppc2parent", VERSION);
        fixtures.setProjectLocales("ppc2parent", "ru-RU");
        fixtures.ensureProject("ppc2child", VERSION);
        fixtures.setProjectLocales("ppc2child", "de-DE");
        fixtures.linkParent("ppc2child", "ppc2parent");

        assertThat(localeIds("ppc2child", VERSION))
                .as("a child that overrides its own locales keeps them")
                .containsExactly("de-DE");
    }

    @Test
    void childInheritsParentProjectType() throws Exception {
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);

        fixtures.ensureProjectNoVersion("ptpparent", "properties");
        fixtures.addVersion("ptpparent", VERSION);
        fixtures.ensureProjectNoVersion("ptpchild", null);
        fixtures.linkParent("ptpchild", "ptpparent");

        HttpResponse<String> resp = get(
                "/rest/projects/p/ptpchild/iterations/i/" + VERSION + "/config");
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode cfg = json.readTree(resp.body());
        assertThat(cfg.path("projectType").asText())
                .as("child with no type inherits the parent's project type")
                .isEqualTo("properties");
    }

    private List<String> localeIds(String slug, String version) throws Exception {
        HttpResponse<String> resp =
                get("/rest/project/" + slug + "/version/" + version + "/locales");
        assertThat(resp.statusCode()).isEqualTo(200);
        JsonNode arr = json.readTree(resp.body());
        return arr.findValuesAsText("localeId");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .GET()
                .build();
        return HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
    }
}
