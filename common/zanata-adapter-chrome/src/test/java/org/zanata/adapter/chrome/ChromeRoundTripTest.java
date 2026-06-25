package org.zanata.adapter.chrome;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

class ChromeRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void readThenWriteReproducesTheSameContent() throws Exception {
        byte[] original = readResource("/messages.json");

        Map<String, ChromeWriter.Entry> entries =
                toEntries(new ChromeReader().extractTemplate("messages",
                        new ByteArrayInputStream(original)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new ChromeWriter().write(w, entries);
        }

        JsonNode before = MAPPER.readTree(original);
        JsonNode after = MAPPER.readTree(out.toByteArray());
        assertThat(after).isEqualTo(before);
    }

    @Test
    void placeholdersSurviveTheRoundTrip() throws Exception {
        Resource resource = new ChromeReader().extractTemplate("messages",
                new ByteArrayInputStream(readResource("/messages.json")));

        TextFlow preset = resource.getTextFlows().stream()
                .filter(tf -> tf.getId().equals("_PresetTitle"))
                .findFirst().orElseThrow();
        ChromeMessage meta = preset.getExtensions().findByType(ChromeMessage.class);

        assertThat(meta).isNotNull();
        assertThat(meta.getPlaceholders().keySet())
                .containsExactly("PRESET", "WIDTH", "HEIGHT");
        assertThat(meta.getPlaceholders().get("PRESET").getContent())
                .isEqualTo("$1");
    }

    @Test
    void descriptionExampleAndLowercasePlaceholderSurviveTheRoundTrip()
            throws Exception {
        String json = """
                {
                    "error": {
                        "message": "Error: $details$",
                        "description": "Generic error template. Expects error parameter to be passed in.",
                        "placeholders": {
                            "details": {
                                "content": "$1",
                                "example": "Failed to fetch RSS feed."
                            }
                        }
                    }
                }""";
        byte[] original = json.getBytes(StandardCharsets.UTF_8);

        Resource resource = new ChromeReader().extractTemplate("messages",
                new ByteArrayInputStream(original));
        TextFlow error = resource.getTextFlows().get(0);
        ChromeMessage meta = error.getExtensions().findByType(ChromeMessage.class);

        assertThat(error.getContents().get(0)).isEqualTo("Error: $details$");
        assertThat(error.getExtensions().findByType(SimpleComment.class).getValue())
                .isEqualTo(
                "Generic error template. Expects error parameter to be passed in.");
        ChromeMessage.Placeholder details = meta.getPlaceholders().get("details");
        assertThat(details.getContent()).isEqualTo("$1");
        assertThat(details.getExample()).isEqualTo("Failed to fetch RSS feed.");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new ChromeWriter().write(w, toEntries(resource));
        }
        assertThat(MAPPER.readTree(out.toByteArray()))
                .isEqualTo(MAPPER.readTree(original));
    }

    private static Map<String, ChromeWriter.Entry> toEntries(Resource resource) {
        Map<String, ChromeWriter.Entry> entries = new LinkedHashMap<>();
        for (TextFlow tf : resource.getTextFlows()) {
            ChromeMessage meta = tf.getExtensions() == null ? null
                    : tf.getExtensions().findByType(ChromeMessage.class);
            SimpleComment comment = tf.getExtensions() == null ? null
                    : tf.getExtensions().findByType(SimpleComment.class);
            String description = comment == null ? null : comment.getValue();
            entries.put(tf.getId(), new ChromeWriter.Entry(
                    tf.getContents().get(0), description, meta));
        }
        return entries;
    }

    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = ChromeRoundTripTest.class.getResourceAsStream(name)) {
            return in.readAllBytes();
        }
    }
}
