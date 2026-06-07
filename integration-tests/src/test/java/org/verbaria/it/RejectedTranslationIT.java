package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;

import org.verbaria.server.headless.service.OfflineExportService;

class RejectedTranslationIT extends AbstractPushPullIT {

    @Test
    void rejectedTranslationIsNotPulled() throws Exception {
        // A rejected translation must never be used: pulling it back must fall
        // back to source, not return the rejected text. Format-independent —
        // the server withholds it before any writer runs.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itrej", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itrej");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itrej", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        translationEditService.changeState(tfId, fr, ContentState.Rejected);

        Files.deleteIfExists(tmp.resolve("messages_fr_FR.properties"));
        PullOptionsImpl pull = pullOpts("trans", "properties", "itrej");
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        Path pulled = tmp.resolve("messages_fr_FR.properties");
        String content = Files.exists(pulled) ? Files.readString(pulled) : "";
        assertThat(content)
                .as("rejected translation must not appear in the pulled file")
                .doesNotContain("Bonjour");
    }

    @Test
    void rejectedTranslationIsExcludedFromExportRegardlessOfFormat()
            throws Exception {
        // Same rule via the offline export (gettext/PO output here) — proving
        // the policy lives in the shared resource builder, not per-format.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itrejexp", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itrejexp");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itrejexp", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        translationEditService.changeState(tfId, fr, ContentState.Rejected);

        OfflineExportService.Bundle bundle = offlineExportService
                .singleTranslatedDoc("itrejexp", VERSION, "messages", fr)
                .orElseThrow();
        String exported = new String(bundle.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(exported)
                .as("rejected translation must not appear in exported %s",
                        bundle.filename())
                .doesNotContain("Bonjour");
    }

    @Test
    void rejectingAnEditExportsThePreviousGoodValue() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itrejprev", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=value1\n");
        pushBoth("itrejprev");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itrejprev", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        translationEditService.save(tfId, fr, "value 1");
        translationEditService.changeState(tfId, fr, ContentState.Rejected);

        OfflineExportService.Bundle bundle = offlineExportService
                .singleTranslatedDoc("itrejprev", VERSION, "messages", fr)
                .orElseThrow();
        String exported = new String(bundle.bytes(), StandardCharsets.UTF_8);
        assertThat(exported)
                .as("after rejecting the edit, export should keep the last good value1 (%s)",
                        bundle.filename())
                .contains("value1")
                .doesNotContain("value 1");
    }
}
