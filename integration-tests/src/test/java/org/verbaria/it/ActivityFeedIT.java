package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.verbaria.server.headless.service.ActivityFeedService;
import org.verbaria.server.headless.service.ActivityFeedService.Entry;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;

class ActivityFeedIT extends AbstractPushPullIT {

    @Autowired
    ActivityFeedService activityFeed;

    private static final String PROJECT = "itactivity";
    private static final String PROJECT2 = "itactivity2";
    private static final LocaleId FR = new LocaleId("fr-FR");

    private Long seedOneTranslation() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT, VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", PROJECT)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId(PROJECT, VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId())
                .get(0).getId();
        // Save (-> Translated, attributed to admin), edit it (records the prior
        // value into history), then approve so there is a before -> after chain
        // and a distinguishable review action.
        translationEditService.save(tfId, FR, "Bonjour", USER);
        translationEditService.save(tfId, FR, "Salut", USER);
        translationEditService.changeState(tfId, FR, ContentState.Approved);
        return tfId;
    }

    @Test
    void recentWithNoFiltersReturnsActivity() throws Exception {
        seedOneTranslation();

        // Regression: with every filter null the query must still run — a bare
        // NULL timestamp bind used to fail on PostgreSQL ("could not determine
        // data type of parameter").
        List<Entry> entries = activityFeed.recent(null, null, null, null, null, 0, 50);

        assertThat(entries).isNotEmpty();
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.actorUsername()).isEqualTo(USER);
            assertThat(e.projectSlug()).isEqualTo(PROJECT);
            assertThat(e.docId()).isEqualTo("messages");
            assertThat(e.localeId()).isEqualTo("fr-FR");
        });
        assertThat(entries).anyMatch(e -> e.state() == ContentState.Approved);
    }

    @Test
    void filtersByUserProjectAndLocale() throws Exception {
        seedOneTranslation();

        assertThat(activityFeed.recent(USER, null, null, null, null, 0, 50))
                .as("matching user").isNotEmpty();
        assertThat(activityFeed.recent("nobody", null, null, null, null, 0, 50))
                .as("unknown user").isEmpty();

        assertThat(activityFeed.recent(null, PROJECT, null, null, null, 0, 50))
                .as("matching project").isNotEmpty();
        assertThat(activityFeed.recent(null, "other-proj", null, null, null, 0, 50))
                .as("unknown project").isEmpty();

        assertThat(activityFeed.recent(null, null, "fr-FR", null, null, 0, 50))
                .as("matching locale").isNotEmpty();
        assertThat(activityFeed.recent(null, null, "de", null, null, 0, 50))
                .as("other locale").isEmpty();
    }

    @Test
    void showsBeforeAndAfterValues() throws Exception {
        seedOneTranslation();

        List<Entry> entries = activityFeed.recent(null, PROJECT, "fr-FR",
                null, null, 0, 50);

        // The edit from "Bonjour" to "Salut" must surface both the new value
        // and the value it replaced.
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.value()).isEqualTo("Salut");
            assertThat(e.previousValue()).isEqualTo("Bonjour");
        });
        // The very first save has no prior value.
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.value()).isEqualTo("Bonjour");
            assertThat(e.previousValue()).isNull();
        });
    }

    @Test
    void showsPreviousValueWhenPriorVersionIsFilteredOut() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT2, VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", PROJECT2)).run();
        HDocument doc = documentRepository
                .findByVersionAndDocId(PROJECT2, VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        // Prior value with no editor attribution (like an import/seed) — it is
        // NOT itself an activity row.
        translationEditService.save(tfId, FR, "Old", null);
        // The current user edits it.
        translationEditService.save(tfId, FR, "New", USER);

        // Filtering to the current user must still surface the prior value as
        // "before", even though the version that holds it is filtered out of
        // the activity rows.
        List<Entry> entries = activityFeed.recent(USER, PROJECT2, "fr-FR",
                null, null, 0, 50);
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.value()).isEqualTo("New");
            assertThat(e.previousValue()).isEqualTo("Old");
        });
    }

    @Test
    void populatesSourceAndResIdForNavigation() throws Exception {
        seedOneTranslation();

        List<Entry> entries = activityFeed.recent(null, PROJECT, "fr-FR",
                null, null, 0, 50);

        assertThat(entries).isNotEmpty();
        assertThat(entries).allSatisfy(e -> {
            // Source text is available so a "saved but empty" row can fall back
            // to it, and resId drives the key deep-link.
            assertThat(e.source()).isEqualTo("Hello");
            assertThat(e.resId()).isNotBlank();
        });
    }

    @Test
    void filtersByDateRange() throws Exception {
        seedOneTranslation();

        Date past = new Date(0L);
        Date now = new Date();
        Date future = new Date(now.getTime() + 86_400_000L);

        assertThat(activityFeed.recent(null, null, null, past, future, 0, 50))
                .as("range covering now").isNotEmpty();
        assertThat(activityFeed.recent(null, null, null, past,
                new Date(1_000L), 0, 50))
                .as("range entirely in the past").isEmpty();
    }

    @Test
    void pagedFetchIsNewestFirstAndBounded() throws Exception {
        seedOneTranslation();

        // The lazy grid pages with (offset, limit) and detects the end when a
        // page returns fewer rows than asked — so there's no count query.
        List<Entry> all = activityFeed.recent(null, PROJECT, "fr-FR",
                null, null, 0, 1000);
        assertThat(all).as("seeded activity is present").isNotEmpty();
        assertThat(all).as("newest first")
                .isSortedAccordingTo(
                        java.util.Comparator.comparing(Entry::when).reversed());

        // A small first page returns just that page.
        assertThat(activityFeed.recent(null, PROJECT, "fr-FR", null, null, 0, 1))
                .hasSize(1);

        // Offset past the end is empty (signals the grid to stop).
        assertThat(activityFeed.recent(null, PROJECT, "fr-FR", null, null,
                all.size(), 10)).isEmpty();
    }
}
