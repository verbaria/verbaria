package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.lock.LockChangelog;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;

class ChangelogIT extends AbstractPushPullIT {

    @Test
    void endToEndChangelogReportsTranslationEdit() throws Exception {
        // playground flow: push both (approved), a translator edits a
        // translation in the editor, pull, diff the locks -> changelog.
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
        push.setIncludes("messages.properties");
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
        push.setIncludes("messages.properties");
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
        push.setIncludes("messages.properties");
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
