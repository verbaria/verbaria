package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import org.verbaria.server.headless.repository.TranslateFilterMode;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;

class NeedsReviewIT extends AbstractPushPullIT {

    @Test
    void needApproveFilterListsTranslatedButNotApproved() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itapprove", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itapprove");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itapprove", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        PageRequest page = PageRequest.of(0, 50);

        translationEditService.save(tfId, fr, "Bonjour");
        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "",
                TranslateFilterMode.NEED_APPROVE.code(), page))
                .as("a translated-but-not-approved flow needs approval")
                .hasSize(1);

        translationEditService.changeState(tfId, fr, ContentState.Approved);

        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "",
                TranslateFilterMode.NEED_APPROVE.code(), page))
                .as("once approved it no longer needs approval")
                .isEmpty();
    }

    @Test
    void sourceEditFlagsTranslationForReviewUntilMarked() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itreview", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itreview");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itreview", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        assertThat(textFlowRepository.countNeedsReviewForDoc(doc.getId(), fr))
                .isZero();

        translationEditService.updateSource(tfId, "Hey");
        assertThat(textFlowRepository.countNeedsReviewForDoc(doc.getId(), fr))
                .as("source edit should flag the existing translation for review")
                .isEqualTo(1L);

        translationEditService.markReviewed(tfId, fr);
        assertThat(textFlowRepository.countNeedsReviewForDoc(doc.getId(), fr))
                .as("markReviewed should clear the needs-review flag")
                .isZero();
    }

    @Test
    void needsReviewFilterAndAggregateCounts() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itreviewq", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itreviewq");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itreviewq", VERSION, "messages").orElseThrow();
        Long iterId = doc.getProjectIteration().getId();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        PageRequest page = PageRequest.of(0, 50);

        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "", 3, page))
                .isEmpty();
        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "", 2, page))
                .hasSize(1);

        translationEditService.updateSource(tfId, "Hey");

        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "", 3, page))
                .as("needs-review filter must list the now-stale translation")
                .hasSize(1);
        assertThat(textFlowRepository.pageForTranslateView(doc.getId(), fr, "", 1, page))
                .isEmpty();

        assertThat(textFlowRepository.countNeedsReviewForDoc(doc.getId(), fr))
                .isEqualTo(1L);
        assertThat(textFlowRepository.countNeedsReviewInIteration(iterId, fr))
                .isEqualTo(1L);
        assertThat(textFlowRepository.countNeedsReviewPerDoc(iterId, fr))
                .singleElement()
                .satisfies(row -> {
                    assertThat(((Number) row[0]).longValue()).isEqualTo(doc.getId());
                    assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
                });
        assertThat(textFlowRepository.countNeedsReviewPerLocale(iterId))
                .anySatisfy(row -> {
                    assertThat(row[0]).isEqualTo(fr);
                    assertThat(((Number) row[1]).longValue()).isEqualTo(1L);
                });
    }
}
