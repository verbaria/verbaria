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

class ChromePushArchiveIT extends AbstractPushPullIT {

    private static final String SOURCE_JSON = """
            {
                "greeting": { "message": "Hello" },
                "error": {
                    "message": "Error: $details$",
                    "description": "Generic error.",
                    "placeholders": { "details": { "content": "$1" } }
                }
            }""";
    private static final String HELLO_RU = "\u041f\u0440\u0438\u0432\u0435\u0442";
    private static final String TRANS_RU_JSON =
            "{ \"greeting\": { \"message\": \"" + HELLO_RU + "\" } }";

    @Test
    void pushArchiveImportsSourceAndTranslationFromZip() throws Exception {
        fixtures.ensureLocale("en");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itarch", VERSION);
        fixtures.setProjectSourceLocale("itarch", "en");

        byte[] zip = zip(
                "src/main/chrome/_locales/en/messages.json", SOURCE_JSON,
                "src/main/chrome/_locales/ru/messages.json", TRANS_RU_JSON);

        PushStatus resp = postArchive("chrome", "itarch", VERSION, zip);
        assertThat(resp.imported()).hasSize(2);

        HDocument doc = documentRepository
                .findByVersionAndDocId("itarch", VERSION, "src/main/chrome/messages")
                .orElseThrow();
        assertThat(doc.getLocale().getLocaleId()).isEqualTo(new LocaleId("en"));
        assertThat(textFlowRepository.findByDocument(doc.getId())).hasSize(2);

        Long greetingTfId = textFlowRepository.findByDocument(doc.getId()).stream()
                .filter(tf -> "Hello".equals(tf.getContents().get(0)))
                .map(tf -> tf.getId()).findFirst().orElseThrow();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(greetingTfId), new LocaleId("ru"));
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(HELLO_RU);
    }

    private PushStatus postArchive(String projectType, String project,
            String version, byte[] zip) throws Exception {
        String boundary = "----it" + System.nanoTime();
        byte[] body = multipart(boundary,
                Map.entry("projectType", projectType),
                Map.entry("project", project),
                Map.entry("version", version),
                Map.entry("@archive", "messages.zip"));
        // body above wrote text fields + the file header; append zip + closer.
        ByteArrayOutputStream full = new ByteArrayOutputStream();
        full.write(body);
        full.write(zip);
        full.write(("\r\n--" + boundary + "--\r\n")
                .getBytes(StandardCharsets.UTF_8));

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
                .as("push-archive response: %s", resp.body())
                .isEqualTo(202);
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
                    .as("push-status response: %s", resp.body())
                    .isEqualTo(200);
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

    @SafeVarargs
    private static byte[] multipart(String boundary,
            Map.Entry<String, String>... parts) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Map.Entry<String, String> p : parts) {
            out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            if (p.getKey().startsWith("@")) {
                String name = p.getKey().substring(1);
                out.write(("Content-Disposition: form-data; name=\"" + name
                        + "\"; filename=\"" + p.getValue() + "\"\r\n"
                        + "Content-Type: application/zip\r\n\r\n")
                        .getBytes(StandardCharsets.UTF_8));
                // file bytes appended by caller
            } else {
                out.write(("Content-Disposition: form-data; name=\"" + p.getKey()
                        + "\"\r\n\r\n" + p.getValue() + "\r\n")
                        .getBytes(StandardCharsets.UTF_8));
            }
        }
        return out.toByteArray();
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
