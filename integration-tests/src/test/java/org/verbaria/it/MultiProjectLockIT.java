package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
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

    private void pushOneProject(String proj) throws Exception {
        PushOptionsImpl push = pushOpts("both", "properties", proj);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        new PushCommand(push).run();
    }
}
