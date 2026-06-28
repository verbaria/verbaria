package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.verbaria.server.headless.service.PushPlanService;
import org.verbaria.server.api.PushPlan;
import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.verbaria.server.headless.changelog.VerbariaLock;
import org.verbaria.server.headless.changelog.VerbariaLockReaderWriter;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlowTarget;

class MultiProjectLockIT extends AbstractPushPullIT {

    @Autowired
    PushPlanService pushPlanService;

    @Test
    void globPushPlanReportsUnmatchedDocuments() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);

        PushPlan plan = pushPlanService.plan("consulo", "zzznomatch**",
                List.of("LOCALIZE-LIB/en_US/messages.yaml"),
                List.of("ru"), "en");

        assertThat(plan.entries())
                .as("no project matches the glob, so nothing is sent")
                .isEmpty();
        assertThat(plan.unmatched())
                .as("the classified document with no owning project must be "
                        + "reported, not silently dropped")
                .hasSize(1);
        assertThat(plan.unmatched().get(0).path())
                .isEqualTo("LOCALIZE-LIB/en_US/messages.yaml");
        assertThat(plan.unmatched().get(0).docId()).isEqualTo("messages");
        assertThat(plan.unmatched().get(0).reason()).contains("zzznomatch**");
    }

    @Test
    void globPullWritesOneLockCoveringEveryProject() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("globcona", VERSION);
        fixtures.ensureProject("globconb", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushOneProject("globcona");
        pushOneProject("globconb");

        PullOptionsImpl pull = pullOpts("both", "properties", "globcon**");
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages*.properties");
        new PullCommand(pull).run();

        VerbariaLock lock = VerbariaLockReaderWriter.readOrNull(
                tmp.resolve("verbaria-lock.json"));
        assertThat(lock).isNotNull();
        assertThat(lock.getDocuments().keySet())
                .as("a glob pull must record every matched project, not just "
                        + "the last one written")
                .contains("globcona/messages", "globconb/messages");
    }

    @Test
    void globPullHonoursPinnedTargetLocales() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureLocale("de-DE");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("globpina", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        Files.writeString(tmp.resolve("messages_de_DE.properties"),
                "greeting=Hallo\n");
        PushOptionsImpl push = pushOpts("both", "properties", "globpina");
        push.setLocaleMapList(frDeLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        Path pullDir = jimfs.getPath("/pull");
        Files.createDirectories(pullDir);
        PullOptionsImpl pull = pullOpts("trans", "properties", "globpin**");
        pull.setSrcDir(pullDir);
        pull.setTransDir(pullDir);
        pull.setProjectConfig(pullDir.resolve("verbaria.json"));
        pull.setLocaleMapList(frLocales());
        new PullCommand(pull).run();

        try (Stream<Path> files = Files.walk(pullDir)) {
            var names = files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString()).toList();
            assertThat(names)
                    .as("the pinned fr-FR translation must be pulled")
                    .anyMatch(n -> n.contains("fr_FR") || n.contains("fr-FR"));
            assertThat(names)
                    .as("a glob pull must not pull the unpinned de-DE locale")
                    .noneMatch(n -> n.contains("de_DE") || n.contains("de-DE"));
        }
    }

    @Test
    void globPushLockHonoursPinnedTargetLocales() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureLocale("de-DE");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("globplock", VERSION);

        Path en = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(en);
        Files.writeString(en.resolve("glplockdoc.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);
        Path fr = tmp.resolve("LOCALIZE-LIB/fr-FR");
        Files.createDirectories(fr);
        Files.writeString(fr.resolve("glplockdoc.yaml"),
                "greeting:\n    text: 'Bonjour'\n", StandardCharsets.UTF_8);
        Path de = tmp.resolve("LOCALIZE-LIB/de-DE");
        Files.createDirectories(de);
        Files.writeString(de.resolve("glplockdoc.yaml"),
                "greeting:\n    text: 'Hallo'\n", StandardCharsets.UTF_8);

        PushOptionsImpl seed = pushOpts("both", "consulo", "globplock");
        seed.setLocaleMapList(frDeLocales());
        new PushCommand(seed).run();

        PushOptionsImpl push = pushOpts("trans", "consulo", "globp**");
        push.setLocaleMapList(frLocales());
        new PushCommand(push).run();

        VerbariaLock lock = VerbariaLockReaderWriter.readOrNull(
                tmp.resolve("verbaria-lock.json"));
        assertThat(lock).isNotNull();
        VerbariaLock.DocumentLock doc = lock.getDocuments().get("globplock/glplockdoc");
        assertThat(doc).as("the glob-pushed doc must be in the lock").isNotNull();
        assertThat(doc.getTranslations().keySet())
                .as("a glob push must record only the pinned locale, not every "
                        + "server locale")
                .containsExactly("fr-FR");
    }

    @Test
    void globTransPushWithoutIncludesScansViaServer() throws Exception {
        String greetingRu = "\u041f\u0440\u0438\u0432\u0435\u0442";
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("globniru", VERSION);
        fixtures.setProjectType("globniru", "consulo");

        Path en = tmp.resolve("src/main/resources/LOCALIZE-LIB/en_US");
        Files.createDirectories(en);
        Files.writeString(en.resolve("mni.yaml"),
                "greeting:\n    text: 'Hello'\n", StandardCharsets.UTF_8);
        Path ru = tmp.resolve("src/main/resources/LOCALIZE-LIB/ru");
        Files.createDirectories(ru);
        Files.writeString(ru.resolve("mni.yaml"),
                "greeting:\n    text: '" + greetingRu + "'\n",
                StandardCharsets.UTF_8);
        Path junk = tmp.resolve("target/classes/LOCALIZE-LIB/ru");
        Files.createDirectories(junk);
        Files.writeString(junk.resolve("mni.yaml"),
                "greeting:\n    text: 'JUNK'\n", StandardCharsets.UTF_8);

        new PushCommand(pushOpts("source", "consulo", "globniru")).run();

        PushOptionsImpl push = pushOpts("trans", "consulo", "globni**");
        push.setProjectType(null);
        push.setIncludes(null);
        push.setExcludes("**/target/**");
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("ru"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("globniru", VERSION, "mni").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("ru"));
        assertThat(targets)
                .as("glob translation push with no includes must store the ru "
                        + "target classified from the LOCALIZE-LIB/ru dir")
                .hasSize(1);
        assertThat(targets.get(0).getContents()).containsExactly(greetingRu);
    }

    @Test
    void globConsuloPullIsFlatWithoutProjectSubdir() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("ru");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("consflata", VERSION);
        fixtures.setProjectType("consflata", "consulo");
        fixtures.ensureProject("consflatb", VERSION);
        fixtures.setProjectType("consflatb", "consulo");

        pushConsulo("consflata", "a.Localize", "Hello A", "\u041f\u0440\u0438\u0432\u0435\u0442 A");
        pushConsulo("consflatb", "b.Localize", "Hello B", "\u041f\u0440\u0438\u0432\u0435\u0442 B");

        Path repo = jimfs.getPath("/consflatout");
        Path lib = repo.resolve("src/main/resources/LOCALIZE-LIB");
        Files.createDirectories(lib);
        PullOptionsImpl pull = pullOpts("trans", "consulo", "consflat**");
        pull.setSrcDir(lib);
        pull.setTransDir(lib);
        pull.setProjectConfig(repo.resolve("verbaria.json"));
        LocaleList ru = new LocaleList();
        ru.add(new LocaleMapping("ru"));
        pull.setLocaleMapList(ru);
        new PullCommand(pull).run();

        assertThat(Files.exists(lib.resolve("ru/a.Localize.yaml")))
                .as("consulo glob pull is flat: doc A under LOCALIZE-LIB/ru")
                .isTrue();
        assertThat(Files.exists(lib.resolve("ru/b.Localize.yaml")))
                .isTrue();
        assertThat(Files.exists(lib.resolve("consflata")))
                .as("no per-project subdirectory for consulo glob pull")
                .isFalse();
        assertThat(Files.exists(lib.resolve("consflatb"))).isFalse();
    }

    private void pushConsulo(String proj, String doc, String en, String ru)
            throws Exception {
        Path base = tmp.resolve(proj);
        Path enDir = base.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(enDir);
        Files.writeString(enDir.resolve(doc + ".yaml"),
                "greeting:\n    text: '" + en + "'\n", StandardCharsets.UTF_8);
        Path ruDir = base.resolve("LOCALIZE-LIB/ru");
        Files.createDirectories(ruDir);
        Files.writeString(ruDir.resolve(doc + ".yaml"),
                "greeting:\n    text: '" + ru + "'\n", StandardCharsets.UTF_8);
        PushOptionsImpl push = pushOpts("both", "consulo", proj);
        push.setSrcDir(base);
        push.setTransDir(base);
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("ru"));
        push.setLocaleMapList(locales);
        new PushCommand(push).run();
    }

    private void pushOneProject(String proj) throws Exception {
        PushOptionsImpl push = pushOpts("both", "properties", proj);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();
    }

    private static LocaleList frDeLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("fr-FR"));
        ll.add(new LocaleMapping("de-DE"));
        return ll;
    }
}
