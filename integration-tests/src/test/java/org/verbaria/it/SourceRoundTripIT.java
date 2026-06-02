package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

class SourceRoundTripIT extends AbstractPushPullIT {

    private static final String PROJECT = "itproj";

    @Test
    void propertiesSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT, VERSION);

        Path src = tmp.resolve("messages.properties");
        Files.writeString(src, "greeting=Hello\nbye=Goodbye\n");

        new PushCommand(pushOpts("source", "properties", PROJECT)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId(PROJECT, VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("Hello", "Goodbye");

        Files.delete(src);
        assertThat(Files.exists(src)).isFalse();

        new PullCommand(pullOpts("source", "properties", PROJECT)).run();

        assertThat(Files.exists(src)).isTrue();
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(src)) {
            p.load(in);
        }
        assertThat(p).as("pulled %s", Files.readString(src)).hasSize(2);
        assertThat(p.stringPropertyNames())
                .as("pulled keys must stay human-readable, not the resId hash: %s",
                        Files.readString(src))
                .containsExactlyInAnyOrder("greeting", "bye");
        assertThat(new java.util.HashSet<>(p.values()))
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @Test
    void gettextSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itgettext", VERSION);

        Path pot = tmp.resolve("messages.pot");
        Files.writeString(pot, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "greeting"
                msgstr ""

                msgid "bye"
                msgstr ""
                """);

        new PushCommand(pushOpts("source", "gettext", "itgettext")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itgettext", VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("greeting", "bye");

        Files.delete(pot);
        assertThat(Files.exists(pot)).isFalse();

        new PullCommand(pullOpts("source", "gettext", "itgettext")).run();

        assertThat(Files.exists(pot)).isTrue();
        String content = Files.readString(pot);
        assertThat(content).contains("greeting").contains("bye");
    }

    @Test
    void consuloSubFileRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulo", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Path anchor = libEn.resolve("my.Localize.yaml");
        Files.writeString(anchor, "greeting:\n    text: Hello\n");
        Path subDir = libEn.resolve("my.Localize");
        Files.createDirectories(subDir);
        Path sub = subDir.resolve("tip.html");
        Files.writeString(sub, "<b>Hi</b>");

        new PushCommand(pushOpts("source", "consulo", "itconsulo")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulo", VERSION, "my.Localize")
                .orElseThrow();
        new TransactionTemplate(txManager)
                .executeWithoutResult(s -> {
            List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
            assertThat(flows).hasSize(2);
            HTextFlow rawSub = flows.stream()
                    .filter(f -> extensionStore
                            .get(f, ConsuloSubFile.class)
                            .isPresent())
                    .findFirst().orElseThrow();
            assertThat(extensionStore
                    .get(rawSub, ConsuloSubFile.class)
                    .orElseThrow().getExtension()).isEqualTo("html");
            assertThat(rawSub.getContents().get(0)).isEqualTo("<b>Hi</b>");
            HTextFlow plain = flows.stream()
                    .filter(f -> extensionStore
                            .get(f, ConsuloSubFile.class)
                            .isEmpty())
                    .findFirst().orElseThrow();
            assertThat(plain.getContents().get(0)).isEqualTo("Hello");
        });

        Files.delete(sub);
        assertThat(Files.exists(sub)).isFalse();

        new PullCommand(pullOpts("source", "consulo", "itconsulo")).run();

        assertThat(Files.exists(sub)).as("sub-file recreated with same extension").isTrue();
        assertThat(Files.readString(sub)).isEqualTo("<b>Hi</b>");
        assertThat(Files.readString(anchor)).contains("greeting").contains("Hello");
    }
}
