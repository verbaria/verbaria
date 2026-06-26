package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;

class TranslationHistoryIT extends AbstractPushPullIT {

    @Test
    void rePushingSameTranslationDoesNotBumpVersionOrAuthor() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itnoop", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itnoop");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itnoop", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        HTextFlowTarget afterFirst = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        Integer v0 = afterFirst.getVersionNum();

        pushBoth("itnoop");

        HTextFlowTarget afterRepush = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(afterRepush.getVersionNum())
                .as("an identical re-push must not bump the target version")
                .isEqualTo(v0);

        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Salut\n");
        pushBoth("itnoop");

        HTextFlowTarget afterEdit = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(afterEdit.getContents()).containsExactly("Salut");
        assertThat(afterEdit.getVersionNum())
                .as("a real content change must advance the target version")
                .isGreaterThan(v0);
    }

    @Test
    void editorSaveCreditsTheEditingUserNotTheOriginalPusher() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureAdmin("translator2", "22222222222222222222222222222222");
        fixtures.ensureProject("itattr", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itattr");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itattr", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        translationEditService.save(tfId, fr, "Salut", "translator2");

        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            HTextFlowTarget t = textFlowTargetRepository
                    .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
            assertThat(t.getContents()).containsExactly("Salut");
            assertThat(t.getTranslator()).isNotNull();
            assertThat(t.getTranslator().getName())
                    .as("the web edit must be credited to the editor")
                    .isEqualTo("translator2");
            assertThat(t.getLastModifiedBy().getName()).isEqualTo("translator2");
        });
    }

    @Test
    void forceSourcePushOverridesUnchangedSourceTarget() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itforcesrc", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        PushOptionsImpl push = pushOpts("source", "properties", "itforcesrc");
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itforcesrc", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId en = new LocaleId("en-US");

        Integer v0 = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), en).get(0).getVersionNum();

        PushOptionsImpl repush = pushOpts("source", "properties", "itforcesrc");
        repush.setIncludes("messages*.properties");
        new PushCommand(repush).run();
        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), en).get(0).getVersionNum())
                .as("a plain source re-push of identical text is a no-op")
                .isEqualTo(v0);

        PushOptionsImpl force = pushOpts("source", "properties", "itforcesrc");
        force.setIncludes("messages*.properties");
        force.setForce(true);
        new PushCommand(force).run();
        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), en).get(0).getVersionNum())
                .as("--force must rewrite the source target even if unchanged")
                .isGreaterThan(v0);
    }

    @Test
    void forcePushOverridesUnchangedTranslation() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itforce", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itforce");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itforce", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        Integer v0 = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0).getVersionNum();

        pushBoth("itforce");
        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0).getVersionNum())
                .as("a plain re-push of identical text is a no-op")
                .isEqualTo(v0);

        PushOptionsImpl force = pushOpts("both", "properties", "itforce");
        force.setLocaleMapList(frLocales());
        force.setIncludes("messages*.properties");
        force.setForce(true);
        new PushCommand(force).run();

        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0).getVersionNum())
                .as("--force must rewrite even identical text, advancing the version")
                .isGreaterThan(v0);
    }

    @Test
    void rePushingTranslationsDoesNotDuplicateHistory() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itdup", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itdup");

        pushBoth("itdup");

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

        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(ids.get(0), new LocaleId("fr-FR"));
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorChangeStateApproveDoesNotDuplicateHistory() throws Exception {
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
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itedit", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itedit");
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
