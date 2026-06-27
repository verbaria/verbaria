package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;

class ChromePushPlanIT extends AbstractPushPullIT {

    private static final String SRC_JSON =
            "{ \"greeting\": { \"message\": \"Hello\" } }";

    private JsonNode plan(String projectType, String project, List<String> paths)
            throws Exception {
        Map<String, Object> body = Map.of(
                "projectType", projectType,
                "project", project,
                "paths", paths);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/rest/push-plan"))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode()).isEqualTo(200);
        return json.readTree(resp.body());
    }

    @Test
    void singleProjectPlanMarksSourceAndTranslation() throws Exception {
        fixtures.ensureLocale("en");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itplan", VERSION);
        fixtures.setProjectSourceLocale("itplan", "en");

        JsonNode result = plan("chrome", "itplan", List.of(
                "src/main/chrome/_locales/en/messages.json",
                "src/main/chrome/_locales/ru/messages.json"));
        JsonNode entries = result.get("entries");

        assertThat(entries).hasSize(2);
        JsonNode en = entries.get(0);
        assertThat(en.get("docId").asText()).isEqualTo("src/main/chrome/messages");
        assertThat(en.get("localeId").asText()).isEqualTo("en");
        assertThat(en.get("project").asText()).isEqualTo("itplan");
        assertThat(en.get("source").asBoolean()).isTrue();

        JsonNode ru = entries.get(1);
        assertThat(ru.get("localeId").asText()).isEqualTo("ru");
        assertThat(ru.get("source").asBoolean()).isFalse();
    }

    @Test
    void globPlanRoutesDocToOwningProject() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("consulo", VERSION);
        fixtures.ensureProject("consulo-java", VERSION);
        fixtures.setProjectSourceLocale("consulo-java", "en");

        // Give consulo-java a doc with the id the chrome path resolves to.
        Path enDir = tmp.resolve("src/main/chrome/_locales/en");
        Files.createDirectories(enDir);
        Files.writeString(enDir.resolve("messages.json"), SRC_JSON,
                StandardCharsets.UTF_8);
        PushOptionsImpl push = pushOpts("source", "chrome", "consulo-java");
        push.setSourceLang("en");
        push.setIncludes("**/_locales/*/messages.json");
        new PushCommand(push).run();

        // A glob push: the path's doc is owned by consulo-java, not consulo.
        JsonNode result = plan("chrome", "consulo**", List.of(
                "src/main/chrome/_locales/ru/messages.json"));
        JsonNode entries = result.get("entries");

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("project").asText())
                .isEqualTo("consulo-java");
        assertThat(entries.get(0).get("docId").asText())
                .isEqualTo("src/main/chrome/messages");
        assertThat(entries.get(0).get("source").asBoolean()).isFalse();
    }
}
