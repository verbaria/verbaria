package org.verbaria.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.model.HDocument;

/**
 * Real consulo plugin repos keep the localize lib nested (e.g.
 * {@code plugin/src/main/resources/LOCALIZE-LIB/en_US/…}) while the pull
 * archive carries canonical paths ({@code en_US/doc.yaml}). The pull must map
 * entries back onto the nested layout — updating the original file and placing
 * new locales next to it — instead of writing copies at the repo root.
 */
class ConsuloNestedPullIT extends AbstractPushPullIT {

    private static final String LIB = "plugin/src/main/resources/LOCALIZE-LIB";
    private static final String INCLUDES =
            "**/src/main/resources/LOCALIZE-LIB/en_US/**/*";

    @Test
    void pullUpdatesNestedOriginalInsteadOfRepoRoot() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulonested", VERSION);
        fixtures.setProjectSourceLocale("itconsulonested", "en-US");

        Path original = tmp.resolve(LIB + "/en_US/messages.yaml");
        Files.createDirectories(original.getParent());
        Files.writeString(original, "greeting:\n    text: 'Hello'\n", UTF_8);

        PushOptionsImpl push = pushOpts("source", "consulo", "itconsulonested");
        push.setIncludes(INCLUDES);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulonested", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        assertThat(approve(tfId, "Bonjour")).isEqualTo(200);

        PullOptionsImpl pull = pullOpts("both", "consulo", "itconsulonested");
        pull.setIncludes(INCLUDES);
        pull.setLocaleMapList(frLocales());
        new PullCommand(pull).run();

        assertThat(Files.readString(original, UTF_8))
                .as("the nested original source file is updated in place")
                .contains("Hello");
        assertThat(Files.exists(tmp.resolve("en_US")))
                .as("no canonical en_US copy at the repo root")
                .isFalse();

        Path translated = tmp.resolve(LIB + "/fr_FR/messages.yaml");
        assertThat(Files.exists(translated))
                .as("the new locale lands next to the existing lib, not at root")
                .isTrue();
        assertThat(Files.readString(translated, UTF_8)).contains("Bonjour");
        assertThat(Files.exists(tmp.resolve("fr_FR")))
                .as("no canonical fr_FR copy at the repo root")
                .isFalse();
    }
}
