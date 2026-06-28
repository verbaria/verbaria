package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.zanata.rest.dto.PushStartResponse;
import org.zanata.rest.dto.PushStatus;
import org.junit.jupiter.api.Test;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlowTarget;

/**
 * Consulo through the NEW generic flow (push-archive + ConsuloDocumentLayout),
 * not the legacy client strategy.
 */
class ConsuloPushArchiveIT extends AbstractPushPullIT {

    private static final String SOURCE_YAML = "greeting:\n    text: 'Hello'\n";
    private static final String GREETING_RU = "\u041f\u0440\u0438\u0432\u0435\u0442";
    private static final String TRANS_RU_YAML =
            "greeting:\n    text: '" + GREETING_RU + "'\n";

    @Test
    void pushArchiveImportsConsuloSourceAndTranslation() throws Exception {
        fixtures.ensureLocale("en");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsuloarch", VERSION);
        fixtures.setProjectSourceLocale("itconsuloarch", "en");

        byte[] zip = zip(
                "LOCALIZE-LIB/en/messages.yaml", SOURCE_YAML,
                "LOCALIZE-LIB/ru/messages.yaml", TRANS_RU_YAML);

        PushStatus resp = postArchive("consulo", "itconsuloarch", VERSION, zip);
        assertThat(resp.imported()).hasSize(2);

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsuloarch", VERSION, "messages")
                .orElseThrow();
        assertThat(doc.getLocale().getLocaleId()).isEqualTo(new LocaleId("en"));
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("ru"));
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(GREETING_RU);
    }

    private PushStatus postArchive(String projectType, String project,
            String version, byte[] zip) throws Exception {
        String boundary = "----it" + System.nanoTime();
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(textPart(boundary, "projectType", projectType));
        full.write(textPart(boundary, "project", project));
        full.write(textPart(boundary, "version", version));
        full.write(("--" + boundary + "\r\nContent-Disposition: form-data; "
                + "name=\"archive\"; filename=\"a.zip\"\r\n"
                + "Content-Type: application/zip\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        full.write(zip);
        full.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/rest/push-archive"))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(full.toByteArray()))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        assertThat(resp.statusCode())
                .as("push-archive response: %s", resp.body()).isEqualTo(202);
        PushStartResponse start =
                json.readValue(resp.body(), PushStartResponse.class);
        return awaitDone(start.sessionId());
    }

    private PushStatus awaitDone(String sessionId) throws Exception {
        for (int i = 0; i < 200; i++) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port
                            + "/rest/push-status/" + sessionId))
                    .header("X-Auth-User", USER)
                    .header("X-Auth-Token", API_KEY)
                    .GET().build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            assertThat(resp.statusCode())
                    .as("push-status response: %s", resp.body()).isEqualTo(200);
            PushStatus st = json.readValue(resp.body(), PushStatus.class);
            if (st.done()) {
                return st;
            }
            if (st.failed()) {
                throw new AssertionError("push failed: " + st.error());
            }
            Thread.sleep(50);
        }
        throw new AssertionError("push did not finish for session " + sessionId);
    }

    private static byte[] textPart(String boundary, String name, String value) {
        return ("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                + name + "\"\r\n\r\n" + value + "\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] zip(String... pathThenContent) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < pathThenContent.length; i += 2) {
                zip.putNextEntry(new ZipEntry(pathThenContent[i]));
                zip.write(pathThenContent[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
