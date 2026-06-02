package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.model.HDocument;

class SourceEditPullIT extends AbstractPushPullIT {

    @Test
    void editedSourceIsPulledAfterApprovedBothPush() throws Exception {
        // Exact playground flow: push --approve --push-type=both, edit the
        // primary source Hello -> Hey in the editor, then pull into the SAME
        // dir (the local messages.properties still holds "Hello").
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itsrc2", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itsrc2");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrc2", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        translationEditService.updateSource(tfId, "Hey");

        // Pull source WITHOUT deleting the local file (it still has "Hello").
        PullOptionsImpl pull = pullOpts("source", "properties", "itsrc2");
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(tmp.resolve("messages.properties"))) {
            p.load(in);
        }
        assertThat(p.getProperty("greeting"))
                .as("edited source must be pulled into the existing file: %s",
                        Files.readString(tmp.resolve("messages.properties")))
                .isEqualTo("Hey");
    }

    @Test
    void editedSourceIsPulledFromDb() throws Exception {
        // Editing the primary/source text in the editor (updateSource) must be
        // reflected on a source pull.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itsrc", VERSION);

        Path src = tmp.resolve("messages.properties");
        Files.writeString(src, "greeting=Hello\n");
        PushOptionsImpl push = pushOpts("source", "properties", "itsrc");
        push.setIncludes("messages.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrc", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        translationEditService.updateSource(tfId, "Hey");

        Files.delete(src);
        PullOptionsImpl pull = pullOpts("source", "properties", "itsrc");
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(src)) {
            p.load(in);
        }
        assertThat(p.getProperty("greeting"))
                .as("edited source must be pulled from DB: %s", Files.readString(src))
                .isEqualTo("Hey");
    }
}
