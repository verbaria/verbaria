package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;

class MultiProjectLockIT extends AbstractPushPullIT {

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
        pull.setIncludes("messages.properties");
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
        push.setIncludes("messages.properties");
        new PushCommand(push).run();

        // Pull into a fresh dir with targetLocales pinned to fr-FR only — even
        // though the server also has a de-DE translation, a glob pull must not
        // pull every server locale, only the pinned one.
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

    private void pushOneProject(String proj) throws Exception {
        PushOptionsImpl push = pushOpts("both", "properties", proj);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        new PushCommand(push).run();
    }

    private static LocaleList frDeLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("fr-FR"));
        ll.add(new LocaleMapping("de-DE"));
        return ll;
    }
}
