package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;

class TranslationPushPullIT extends AbstractPushPullIT {

    @Test
    void propertiesTranslationLandsInDb() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("ittrans", VERSION);

        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\nbye=Au revoir\n");

        PushOptionsImpl push = pushOpts("both", "properties", "ittrans");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("fr-FR"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("ittrans", VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);

        List<Long> ids = flows.stream().map(HTextFlow::getId).toList();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(ids, new LocaleId("fr-FR"));

        assertThat(targets)
                .as("fr-FR translations must be persisted after `push --push-type both`")
                .hasSize(2);
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .containsExactlyInAnyOrder("Bonjour", "Au revoir");
    }

    @Test
    void translationRepushIsIdempotent() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("ittransrepush", VERSION);

        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\nbye=Au revoir\n");

        PushOptionsImpl push = pushOpts("both", "properties", "ittransrepush");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        Map<String, String> before = transSnapshot("ittransrepush");
        assertThat(before).hasSize(2);

        PushOptionsImpl repush = pushOpts("both", "properties", "ittransrepush");
        repush.setLocaleMapList(frLocales());
        repush.setIncludes("messages*.properties");
        new PushCommand(repush).run();

        assertThat(transSnapshot("ittransrepush"))
                .as("re-pushing identical fr-FR translations must not change any "
                        + "target row (version, author, lastChanged) or add "
                        + "history")
                .isEqualTo(before);
    }

    private Map<String, String> transSnapshot(String project) {
        return new TransactionTemplate(txManager).execute(s -> {
            HDocument doc = documentRepository
                    .findByVersionAndDocId(project, VERSION, "messages")
                    .orElseThrow();
            LocaleId fr = new LocaleId("fr-FR");
            Map<String, String> snap = new java.util.HashMap<>();
            for (HTextFlow f : textFlowRepository.findByDocument(doc.getId())) {
                textFlowTargetRepository.findByTextFlowAndLocale(f.getId(), fr)
                        .ifPresent(t -> {
                            int history = textFlowTargetHistoryRepository
                                    .findByTextFlowAndLocale(f.getId(), fr).size();
                            snap.put(f.getResId(), "ver=" + t.getVersionNum()
                                    + " state=" + t.getState()
                                    + " contents=" + t.getContents()
                                    + " translator=" + personName(t.getTranslator())
                                    + " modBy=" + personName(t.getLastModifiedBy())
                                    + " changed=" + t.getLastChanged()
                                    + " history=" + history);
                        });
            }
            return snap;
        });
    }

    private static String personName(org.zanata.model.HPerson p) {
        return p == null ? "null" : p.getName();
    }

    @Test
    void translationPullKeepsHumanReadableKeys() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itkeys", VERSION);

        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\nbye=Au revoir\n");
        pushBoth("itkeys");

        Files.deleteIfExists(tmp.resolve("messages_fr_FR.properties"));
        PullOptionsImpl pull = pullOpts("both", "properties", "itkeys");
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages*.properties");
        new PullCommand(pull).run();

        Path pulled = tmp.resolve("messages_fr_FR.properties");
        assertThat(Files.exists(pulled)).isTrue();
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(pulled)) {
            p.load(in);
        }
        assertThat(p.stringPropertyNames())
                .as("pulled translation keys must be human-readable: %s",
                        Files.readString(pulled))
                .containsExactlyInAnyOrder("greeting", "bye");
        assertThat(new java.util.HashSet<>(p.values()))
                .containsExactlyInAnyOrder("Bonjour", "Au revoir");
    }
}
