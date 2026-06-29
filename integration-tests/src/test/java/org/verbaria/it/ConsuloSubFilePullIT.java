package org.verbaria.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.verbaria.server.api.PushStartResponse;
import org.verbaria.server.api.PushStatus;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;

/**
 * End-to-end guard for the consulo translation-pull bug: a raw sub-file entry
 * (one carrying a {@code ConsuloSubFile} extension) must be pulled back as its
 * own external file with the translated content, not inlined in the yaml.
 */
class ConsuloSubFilePullIT extends AbstractPushPullIT {

    private static final String TRANSLATED = "<b>\u043f\u0435\u0440\u0435\u0432\u043e\u0434</b>";

    @Test
    void pullWritesSubFileTranslationAsExternalFile() throws Exception {
        fixtures.ensureLocale("en");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulosub", VERSION);
        fixtures.setProjectSourceLocale("itconsulosub", "en");

        // Source: an inline yaml entry plus a raw sub-file (docs/readme.html).
        byte[] zip = zip(
                "LOCALIZE-LIB/en/messages.yaml", "greeting:\n    text: 'Hello'\n",
                "LOCALIZE-LIB/en/messages/docs/readme.html", "<b>source</b>");
        PushStatus resp = postArchive("consulo", "itconsulosub", VERSION, zip);
        assertThat(resp.imported()).isNotEmpty();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulosub", VERSION, "messages")
                .orElseThrow();
        // The raw sub-file flow is the one carrying the source file content
        // (the resId is hashed on import, so match by content).
        HTextFlow sub = textFlowRepository.findByDocument(doc.getId()).stream()
                .filter(tf -> !tf.getContents().isEmpty()
                        && "<b>source</b>".equals(tf.getContents().get(0)))
                .findFirst().orElseThrow();

        // Translate (approve) the sub-file entry into fr-FR.
        assertThat(approve(sub.getId(), TRANSLATED)).isEqualTo(200);

        Map<String, byte[]> pulled =
                pullArchive("consulo", "itconsulosub", VERSION, "trans", "fr-FR");

        // The sub-file translation is written as its own external file…
        Map.Entry<String, byte[]> html = pulled.entrySet().stream()
                .filter(e -> e.getKey().endsWith(".html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no external .html translation file in " + pulled.keySet()));
        assertThat(new String(html.getValue(), UTF_8)).isEqualTo(TRANSLATED);

        // …and never inlined into any yaml.
        pulled.forEach((name, bytes) -> {
            if (name.endsWith(".yaml")) {
                assertThat(new String(bytes, UTF_8)).doesNotContain(TRANSLATED);
            }
        });
    }

    private Map<String, byte[]> pullArchive(String projectType, String project,
            String version, String pullType, String locale) throws Exception {
        String url = "http://localhost:" + port + "/rest/pull-archive"
                + "?projectType=" + projectType + "&project=" + project
                + "&version=" + version + "&pullType=" + pullType
                + "&targetLocales=" + locale;
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .GET().build();
        HttpResponse<byte[]> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofByteArray());
        assertThat(resp.statusCode()).isEqualTo(200);
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zis =
                new ZipInputStream(new ByteArrayInputStream(resp.body()))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                out.put(e.getName(), zis.readAllBytes());
            }
        }
        return out;
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
