package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.ActivityType;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.Activity;
import org.zanata.model.HDocument;

import org.verbaria.server.headless.repository.ActivityRepository;

class ActivityIT extends AbstractPushPullIT {

    @Autowired
    ActivityRepository activityRepository;

    @Test
    void everyActivityTypeIsRecorded() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itactivity", VERSION);

        // UPLOAD_SOURCE_DOCUMENT — push the source bundle.
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        new PushCommand(pushOpts("source", "properties", "itactivity")).run();
        assertThat(typesFor(USER)).contains(ActivityType.UPLOAD_SOURCE_DOCUMENT);

        // UPLOAD_TRANSLATION_DOCUMENT — push a fr-FR translation.
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itactivity");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        new PushCommand(push).run();
        assertThat(typesFor(USER))
                .contains(ActivityType.UPLOAD_TRANSLATION_DOCUMENT);

        HDocument doc = documentRepository
                .findByVersionAndDocId("itactivity", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        // UPDATE_TRANSLATION — edit the translation in the editor.
        translationEditService.save(tfId, fr, "Salut", USER);
        assertThat(typesFor(USER)).contains(ActivityType.UPDATE_TRANSLATION);

        // REVIEWED_TRANSLATION — approve it.
        translationEditService.changeState(tfId, fr, ContentState.Approved, null,
                USER);
        assertThat(typesFor(USER)).contains(ActivityType.REVIEWED_TRANSLATION);

        assertThat(typesFor(USER))
                .as("all four activity types must be recorded for the actor")
                .contains(ActivityType.UPLOAD_SOURCE_DOCUMENT,
                        ActivityType.UPLOAD_TRANSLATION_DOCUMENT,
                        ActivityType.UPDATE_TRANSLATION,
                        ActivityType.REVIEWED_TRANSLATION);
    }

    private Set<ActivityType> typesFor(String username) {
        return activityRepository.findByActor(username, PageRequest.of(0, 50))
                .stream().map(Activity::getActivityType)
                .collect(Collectors.toSet());
    }
}
