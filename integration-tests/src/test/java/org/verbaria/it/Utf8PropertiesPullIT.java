package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

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

class Utf8PropertiesPullIT extends AbstractPushPullIT {

    private static final String PROJECT = "itutf8pull";
    private static final String KEY = "title.select.path.to.browser";

    @Test
    void pullRestoresHumanKeysAndSkipsSourceLocale() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT, VERSION);

        Files.writeString(tmp.resolve("messages.properties"),
                KEY + "=Select Path to Browser\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                KEY + "=Chemin du navigateur\n");

        PushOptionsImpl push = pushOpts("both", "properties", PROJECT);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        new PushCommand(push).run();

        try (Stream<Path> s = Files.list(tmp)) {
            for (Path p : s.toList()) {
                Files.deleteIfExists(p);
            }
        }
        PullOptionsImpl pull = pullOpts("trans", "utf8properties", PROJECT);
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("fr-FR"));
        locales.add(new LocaleMapping("en-US"));
        pull.setLocaleMapList(locales);
        new PullCommand(pull).run();

        Path frFile;
        try (Stream<Path> s = Files.list(tmp)) {
            frFile = s.filter(p -> {
                        String n = p.getFileName().toString();
                        return n.startsWith("messages_fr")
                                && n.endsWith(".properties");
                    })
                    .findFirst().orElseThrow();
        }
        String fr = Files.readString(frFile);
        assertThat(fr)
                .as("pulled translation must keep the human key, not the hash: %s", fr)
                .contains(KEY + "=Chemin du navigateur");

        List<String> enTransFiles;
        try (Stream<Path> s = Files.list(tmp)) {
            enTransFiles = s.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("messages_en") && n.endsWith(".properties"))
                    .toList();
        }
        assertThat(enTransFiles)
                .as("source locale must not be pulled as a translation file")
                .isEmpty();
    }
}
