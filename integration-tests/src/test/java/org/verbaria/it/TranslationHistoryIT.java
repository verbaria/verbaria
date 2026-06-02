package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;

class TranslationHistoryIT extends AbstractPushPullIT {

    @Test
    void rePushingTranslationsDoesNotDuplicateHistory() throws Exception {
        // Reproduces the "duplicate key (target_id, version_num)" crash. The
        // import wrote a history row by hand on every re-push of an already
        // translated string — even when nothing changed, so the target's
        // @Version stayed put. That leaves an orphan history row at the current
        // version; the next real edit's @PreUpdate listener then writes history
        // at the SAME version, violating the (target_id, version_num) natural id.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itdup", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itdup");

        // Re-push the SAME translation — nothing changes, so the version must
        // not advance and no history should be written.
        pushBoth("itdup");

        // Now change it and push again, exactly as a translator would.
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Salut\n");
        pushBoth("itdup");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itdup", VERSION, "messages")
                .orElseThrow();
        List<Long> ids = textFlowRepository.findByDocument(doc.getId()).stream()
                .map(HTextFlow::getId).toList();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(ids, new LocaleId("fr-FR"));
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .as("the re-pushed translation must be persisted")
                .containsExactly("Salut");

        // The crash was a duplicate (target_id, version_num). Production rejects
        // it; the test DB silently keeps both rows, so assert on the rows
        // directly — every version number must appear at most once.
        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(ids.get(0), new LocaleId("fr-FR"));
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorChangeStateApproveDoesNotDuplicateHistory() throws Exception {
        // The actual editor crash path: TranslationEditService.changeState(...,
        // Approved). It used to write a history row by hand on top of the one
        // the @PreUpdate listener writes, so the very next state change violated
        // the (target_id, version_num) natural id — the production
        // "duplicate key value violates unique constraint" 23505 error.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itstate", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itstate");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itstate", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        // Edit a couple of times to advance the @Version, mirroring a real
        // translator session before the approve that crashed at version 2.
        translationEditService.save(tfId, fr, "Salut");
        translationEditService.save(tfId, fr, "Coucou");

        ContentState state = translationEditService.changeState(
                tfId, fr, ContentState.Approved);
        assertThat(state)
                .as("approve must persist, not crash on duplicate history")
                .isEqualTo(ContentState.Approved);

        HTextFlowTarget target = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(target.getState()).isEqualTo(ContentState.Approved);
        assertThat(target.getContents()).containsExactly("Coucou");

        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, fr);
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorReApproveIsIdempotent() throws Exception {
        // Re-approving an already-approved row must be a harmless no-op (the UI
        // can leave the button enabled), not a duplicate-history crash.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itidem", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itidem");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itidem", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        translationEditService.changeState(tfId, fr, ContentState.Approved);
        translationEditService.changeState(tfId, fr, ContentState.Approved);
        ContentState state = translationEditService
                .changeState(tfId, fr, ContentState.Approved);

        assertThat(state).isEqualTo(ContentState.Approved);
        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, fr);
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorApproveAfterRePushPersistsAndDoesNotDuplicateHistory()
            throws Exception {
        // The exact playground report: the translation is pushed (and re-pushed,
        // which left an orphan history row at the target's current version while
        // its @Version stayed put), then a translator approves an edit in the
        // editor. The approve's @PreUpdate listener writes history at that same
        // version, hits the duplicate (target_id, version_num) constraint, the
        // transaction rolls back — so the approved text is never saved ("I can't
        // see Hey") and re-trying surfaces the duplicate-key error.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itedit", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itedit");
        // Re-push the same translation: the buggy import leaves an orphan
        // history row at the current (unchanged) version.
        pushBoth("itedit");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itedit", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId())
                .get(0).getId();

        int approved = approve(tfId, "Hey");
        assertThat(approved)
                .as("editor approve must succeed, not 500 on duplicate history")
                .isEqualTo(200);

        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("fr-FR"));
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .as("the approved translation must actually be persisted")
                .containsExactly("Hey");

        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, new LocaleId("fr-FR"));
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }
}
