package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.changelog.ChangelogCommand;
import org.zanata.client.commands.changelog.ChangelogOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.verbaria.server.headless.changelog.LockChangelog;
import org.verbaria.server.headless.changelog.VerbariaLock;
import org.verbaria.server.headless.changelog.VerbariaLock.TranslationLock;
import org.verbaria.server.headless.changelog.VerbariaLockReaderWriter;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;

class ChangelogIT extends AbstractPushPullIT {

    private VerbariaLock sampleLock(String sig, String generatedAt) {
        VerbariaLock lock = new VerbariaLock();
        lock.setProject("itclend");
        lock.setProjectVersion(VERSION);
        lock.setSourceLocale("en-US");
        lock.setGeneratedAt(generatedAt);
        lock.document("messages").getTranslations().put("fr-FR",
                new TranslationLock(sig, "Translated", 1,
                        List.of("Alice <alice@x.org>")));
        return lock;
    }

    private File runChangelog(File oldF, File newF, String format)
            throws Exception {
        File out = oldF.toPath().getParent().resolve("out-" + format + ".txt")
                .toFile();
        ChangelogOptionsImpl opts = new ChangelogOptionsImpl();
        opts.setUrl(URI.create("http://localhost:" + port + "/").toURL());
        opts.setUsername(USER);
        opts.setKey(API_KEY);
        opts.setOldLock(oldF);
        opts.setNewLock(newF);
        opts.setFormat(format);
        opts.setOutput(out);
        new ChangelogCommand(opts).runWithActions();
        return out;
    }

    @Test
    void changelogEndpointReturnsEmptyForGeneratedAtOnlyDiff() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        Path dir = Files.createTempDirectory("changelog-it");
        File oldF = dir.resolve("verbaria-lock.old.json").toFile();
        File newF = dir.resolve("verbaria-lock.json").toFile();
        VerbariaLockReaderWriter.write(
                sampleLock("S1", "2026-06-27T10:50:37.552376661Z"),
                oldF.toPath());
        VerbariaLockReaderWriter.write(
                sampleLock("S1", "2026-06-27T11:09:44.303295402Z"),
                newF.toPath());

        File out = runChangelog(oldF, newF, "git-commit");
        assertThat(out).exists();
        assertThat(out.length())
                .as("a generatedAt-only diff must produce an empty file so the "
                        + "sync action opens no PR")
                .isZero();
    }

    @Test
    void changelogEndpointReportsRealTranslationChange() throws Exception {
        fixtures.ensureAdmin(USER, API_KEY);
        Path dir = Files.createTempDirectory("changelog-it");
        File oldF = dir.resolve("verbaria-lock.old.json").toFile();
        File newF = dir.resolve("verbaria-lock.json").toFile();
        VerbariaLockReaderWriter.write(
                sampleLock("S1", "2026-06-27T10:50:37.552376661Z"),
                oldF.toPath());
        VerbariaLockReaderWriter.write(
                sampleLock("S2", "2026-06-27T11:09:44.303295402Z"),
                newF.toPath());

        File out = runChangelog(oldF, newF, "git-commit");
        String body = Files.readString(out.toPath());
        assertThat(body)
                .as("a real signature change must produce a commit message")
                .contains("messages")
                .contains("fr-FR")
                .contains("Co-authored-by: Alice <alice@x.org>");
    }

    @Test
    void endToEndChangelogReportsTranslationEdit() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itclt", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itclt");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        VerbariaLock oldLock = pullBothAndReadLock("itclt");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itclt", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        translationEditService.save(tfId, new LocaleId("fr-FR"), "Salut");

        VerbariaLock newLock = pullBothAndReadLock("itclt");

        String changelog = LockChangelog.render(oldLock, newLock);
        assertThat(changelog)
                .as("changelog must report the fr-FR translation edit")
                .isNotEmpty()
                .contains("messages")
                .contains("fr-FR");
    }

    @Test
    void sourceEditReportedViaBaseLocaleTargetEvenWithoutSourceSig() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itclm", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itclm");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        VerbariaLock oldLock = pullBothAndReadLock("itclm");
        oldLock.getDocuments().get("messages").getSource().setSig(null);

        HDocument doc = documentRepository
                .findByVersionAndDocId("itclm", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        translationEditService.updateSource(tfId, "Hey", USER);

        VerbariaLock newLock = pullBothAndReadLock("itclm");

        String changelog = LockChangelog.render(oldLock, newLock);
        assertThat(changelog)
                .as("a source edit is reported via the base-locale target even "
                        + "when the source-sig baseline is missing; old=%s new=%s",
                        VerbariaLockReaderWriter.toJson(oldLock),
                        VerbariaLockReaderWriter.toJson(newLock))
                .isNotEmpty()
                .contains("messages")
                .contains("en-US");
    }

    @Test
    void endToEndChangelogReportsSourceEdit() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itcls", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itcls");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages*.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        VerbariaLock oldLock = pullBothAndReadLock("itcls");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itcls", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        translationEditService.updateSource(tfId, "Hey", USER);

        VerbariaLock newLock = pullBothAndReadLock("itcls");

        String changelog = LockChangelog.render(oldLock, newLock);
        assertThat(changelog)
                .as("changelog must report the source edit with its author; "
                        + "old=%s new=%s",
                        VerbariaLockReaderWriter.toJson(oldLock),
                        VerbariaLockReaderWriter.toJson(newLock))
                .isNotEmpty()
                .contains("messages")
                .contains("en-US")
                .contains("Co-authored-by: admin <admin@it.local>");
    }
}
