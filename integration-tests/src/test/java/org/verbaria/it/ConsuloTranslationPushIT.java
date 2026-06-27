package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
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

/**
 * Consulo translation push for a zh-TW locale: a {@code LOCALIZE-LIB/<locale>}
 * translation yaml must land on the server as a target for that locale.
 */
class ConsuloTranslationPushIT extends AbstractPushPullIT {

    private static final String GREETING_ZH = "\u4f60\u597d"; // 你好

    @Test
    void pushesConsuloZhTwTranslation() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("zh-TW");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulozh", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);

        // Folder named after the server locale id (zh-TW), so the client finds it.
        Path libZh = tmp.resolve("LOCALIZE-LIB/zh-TW");
        Files.createDirectories(libZh);
        Files.writeString(libZh.resolve("messages.yaml"),
                "greeting:\n    text: '" + GREETING_ZH + "'\n",
                StandardCharsets.UTF_8);

        PushOptionsImpl push = pushOpts("both", "consulo", "itconsulozh");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("zh-TW"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulozh", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("zh-TW"));
        assertThat(targets)
                .as("the zh-TW consulo translation must be stored")
                .hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(GREETING_ZH);
    }

    @Test
    void pushesConsuloZhTwTranslationFromUnderscoreFolder() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("zh-TW");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulozhus", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);

        // Consulo lays the folder out Java-style (zh_TW) while the server locale
        // id is zh-TW — this is the layout that actually ships.
        Path libZh = tmp.resolve("LOCALIZE-LIB/zh_TW");
        Files.createDirectories(libZh);
        Files.writeString(libZh.resolve("messages.yaml"),
                "greeting:\n    text: '" + GREETING_ZH + "'\n",
                StandardCharsets.UTF_8);

        PushOptionsImpl push = pushOpts("both", "consulo", "itconsulozhus");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("zh-TW"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulozhus", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("zh-TW"));
        assertThat(targets)
                .as("a zh_TW folder must still push to the zh-TW locale")
                .hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(GREETING_ZH);
    }

    @Test
    void pushesConsuloZhTwTranslationWithUnderscoreLocaleConfig() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("zh-TW");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulozhcfg", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);
        Path libZh = tmp.resolve("LOCALIZE-LIB/zh_TW");
        Files.createDirectories(libZh);
        Files.writeString(libZh.resolve("messages.yaml"),
                "greeting:\n    text: '" + GREETING_ZH + "'\n",
                StandardCharsets.UTF_8);

        // The config lists the locale Java-style (zh_TW). It must be sent to the
        // server as the BCP-47 zh-TW, not rejected with
        // "expected lang[-country[-modifier]], got zh_TW".
        PushOptionsImpl push = pushOpts("both", "consulo", "itconsulozhcfg");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("zh_TW"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulozhcfg", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("zh-TW"));
        assertThat(targets)
                .as("a zh_TW locale config must push to the zh-TW server locale")
                .hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(GREETING_ZH);
    }

    @Test
    void pullTransSkipsLocalesWithNoTranslations() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsuloempty", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);

        new PushCommand(pushOpts("source", "consulo", "itconsuloempty")).run();

        Path out = tmp.resolve("pulled");
        Files.createDirectories(out);
        PullOptionsImpl pull = pullOpts("trans", "consulo", "itconsuloempty");
        pull.setTransDir(out);
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("ru"));
        pull.setLocaleMapList(locales);
        new PullCommand(pull).run();

        try (Stream<Path> walk = Files.walk(out)) {
            assertThat(walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".yaml")).toList())
                    .as("no empty translation yaml must be written for a locale "
                            + "that has no translations")
                    .isEmpty();
        }
    }
}
