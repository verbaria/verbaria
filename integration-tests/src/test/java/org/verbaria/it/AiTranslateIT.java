package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.verbaria.server.headless.service.AiTranslationService;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;

class AiTranslateIT extends AbstractPushPullIT {

    @Autowired
    AiTranslationService aiTranslationService;

    @Test
    void singleAiTranslateSavesAndApprovesForReviewer() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itai1", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", "itai1")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itai1", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        AiTranslationService.OneResult res = aiTranslationService.translateOne(
                tfId, fr, TestTranslationProvider.ID, USER);

        assertThat(res.content()).isEqualTo(TestTranslationProvider.expected("Hello"));
        assertThat(res.applied()).isTrue();
        assertThat(res.state())
                .as("an admin/reviewer's AI translation into an empty slot is auto-approved")
                .isEqualTo(ContentState.Approved);

        HTextFlowTarget t = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(t.getContents())
                .containsExactly(TestTranslationProvider.expected("Hello"));
        assertThat(t.getState()).isEqualTo(ContentState.Approved);
    }

    @Test
    void aiTranslateDoesNotClobberAnExistingTranslation() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itaikeep", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", "itaikeep")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itaikeep", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        // A human translation already exists.
        translationEditService.save(tfId, fr, "Bonjour", USER);

        AiTranslationService.OneResult res = aiTranslationService.translateOne(
                tfId, fr, TestTranslationProvider.ID, USER);

        assertThat(res.content())
                .as("the AI suggestion is still returned for the editor to show")
                .isEqualTo(TestTranslationProvider.expected("Hello"));
        assertThat(res.applied())
                .as("an existing translation must not be auto-overwritten")
                .isFalse();

        HTextFlowTarget t = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(t.getContents())
                .as("the stored translation is untouched")
                .containsExactly("Bonjour");
    }

    @Test
    void singleAiTranslateLeavesTranslatedForNonReviewer() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureUser("plainuser", "33333333333333333333333333333333");
        fixtures.ensureProject("itai2", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", "itai2")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itai2", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        AiTranslationService.OneResult res = aiTranslationService.translateOne(
                tfId, fr, TestTranslationProvider.ID, "plainuser");

        assertThat(res.state())
                .as("a non-reviewer's AI translation stays Translated (needs approval)")
                .isEqualTo(ContentState.Translated);
        HTextFlowTarget t = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(t.getState()).isEqualTo(ContentState.Translated);
    }

    @Test
    void bulkAiTranslatesAndApprovesEveryUntranslatedFlow() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itaibulk", VERSION);
        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");
        new PushCommand(pushOpts("source", "properties", "itaibulk")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itaibulk", VERSION, "messages").orElseThrow();
        LocaleId fr = new LocaleId("fr-FR");

        AiTranslationService.BulkResult res = aiTranslationService
                .translateUntranslated(doc.getId(), fr, TestTranslationProvider.ID,
                        USER, 1000, ContentState.Approved);

        assertThat(res.translated()).isEqualTo(2);
        assertThat(res.approved()).isEqualTo(2);

        List<Long> ids = textFlowRepository.findByDocument(doc.getId()).stream()
                .map(HTextFlow::getId).toList();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(ids, fr);
        assertThat(targets).hasSize(2);
        assertThat(targets).allSatisfy(t ->
                assertThat(t.getState()).isEqualTo(ContentState.Approved));
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .containsExactlyInAnyOrder(
                        TestTranslationProvider.expected("Hello"),
                        TestTranslationProvider.expected("Goodbye"));
    }
}
