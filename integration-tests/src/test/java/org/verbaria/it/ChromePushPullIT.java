package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlowTarget;

class ChromePushPullIT extends AbstractPushPullIT {

    private static final String DOC_ID = "src/main/chrome/messages";

    /** A messages.json exercising every documented field of the format. */
    private static final String ALL_OPTIONS_JSON = """
            {
                "simple": {
                    "message": "Just a message"
                },
                "described": {
                    "message": "Has a description",
                    "description": "Context shown to translators."
                },
                "onePlaceholderNoExample": {
                    "message": "Hi $name$",
                    "placeholders": {
                        "name": {
                            "content": "$1"
                        }
                    }
                },
                "onePlaceholderWithExample": {
                    "message": "Error: $details$",
                    "description": "Generic error template.",
                    "placeholders": {
                        "details": {
                            "content": "$1",
                            "example": "Failed to fetch RSS feed."
                        }
                    }
                },
                "manyPlaceholders": {
                    "message": "$preset$ ($width$ x $height$)",
                    "placeholders": {
                        "preset": { "content": "$1", "example": "Desktop" },
                        "width": { "content": "$2", "example": "1920" },
                        "height": { "content": "$3" }
                    }
                },
                "unicodeAndEscapes": {
                    "message": "\\u041f\\u0440\\u0438\\u0432\\u0435\\u0442 \\"x\\"\\n%s",
                    "description": "unicode, quotes and newline"
                }
            }""";

    @Test
    void fullRoundTripPushThenPullKeepsDataIdentical() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itchromefull", VERSION);

        Path enDir = tmp.resolve("src/main/chrome/_locales/en");
        Files.createDirectories(enDir);
        Files.writeString(enDir.resolve("messages.json"), ALL_OPTIONS_JSON,
                StandardCharsets.UTF_8);

        PushOptionsImpl push = pushOpts("source", "chrome", "itchromefull");
        push.setSourceLang("en");
        push.setIncludes("**/_locales/*/messages.json");
        new PushCommand(push).run();

        // Pull source into a clean tree and compare to the original byte input.
        Path out = tmp.resolve("pulled");
        Files.createDirectories(out);
        PullOptionsImpl pull = pullOpts("source", "chrome", "itchromefull");
        pull.setSrcDir(out);
        pull.setTransDir(out);
        pull.setIncludes("**/_locales/*/messages.json");
        new PullCommand(pull).run();

        Path pulled = out.resolve("src/main/chrome/_locales/en/messages.json");
        assertThat(Files.isRegularFile(pulled)).isTrue();

        JsonNode before = json.readTree(ALL_OPTIONS_JSON);
        JsonNode after = json.readTree(Files.readAllBytes(pulled));
        assertThat(after)
                .as("pushed-then-pulled messages.json must be identical")
                .isEqualTo(before);
    }

    @Test
    void pushStoresProjectSourceLocaleNotCliDefault() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itchromesrc", VERSION);
        fixtures.setProjectSourceLocale("itchromesrc", "en");

        Path enDir = tmp.resolve("src/main/chrome/_locales/en");
        Files.createDirectories(enDir);
        Files.writeString(enDir.resolve("messages.json"), ALL_OPTIONS_JSON,
                StandardCharsets.UTF_8);

        // No setSourceLang(): the CLI default is en-US, but the project's
        // configured source locale (en) must win on the server.
        PushOptionsImpl push = pushOpts("source", "chrome", "itchromesrc");
        push.setIncludes("**/_locales/*/messages.json");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itchromesrc", VERSION, DOC_ID)
                .orElseThrow();
        assertThat(doc.getLocale().getLocaleId())
                .as("source doc must use the project source locale, not en-US")
                .isEqualTo(new LocaleId("en"));
    }

    @Test
    void pushesRussianTranslationByDirName() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itchrome", VERSION);

        Path enDir = tmp.resolve("src/main/chrome/_locales/en");
        Files.createDirectories(enDir);
        Files.writeString(enDir.resolve("messages.json"), ALL_OPTIONS_JSON,
                StandardCharsets.UTF_8);
        Path ruDir = tmp.resolve("src/main/chrome/_locales/ru");
        Files.createDirectories(ruDir);
        Files.writeString(ruDir.resolve("messages.json"), """
                {
                    "simple": { "message": "\\u041f\\u0440\\u043e\\u0441\\u0442\\u043e" }
                }""", StandardCharsets.UTF_8);

        PushOptionsImpl push = pushOpts("both", "chrome", "itchrome");
        push.setSourceLang("en");
        push.setIncludes("**/_locales/*/messages.json");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("ru"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itchrome", VERSION, DOC_ID).orElseThrow();
        Long simpleTfId = textFlowRepository.findByDocument(doc.getId()).stream()
                .filter(tf -> "Just a message".equals(tf.getContents().get(0)))
                .map(tf -> tf.getId()).findFirst().orElseThrow();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(simpleTfId),
                        new LocaleId("ru"));
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).getContents())
                .containsExactly("\u041f\u0440\u043e\u0441\u0442\u043e");
    }
}
