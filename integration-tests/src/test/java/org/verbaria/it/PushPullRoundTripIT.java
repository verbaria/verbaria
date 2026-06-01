package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.lock.LockChangelog;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;

import org.zanata.model.HTextFlowTargetHistory;

import org.verbaria.server.headless.service.OfflineExportService;
import org.verbaria.server.headless.service.TranslationEditService;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

@SpringBootTest(classes = ItApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PushPullRoundTripIT {

    private static final String PROJECT = "itproj";
    private static final String VERSION = "master";
    private static final String USER = "admin";
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Autowired
    ItFixtures fixtures;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    TextFlowRepository textFlowRepository;
    @Autowired
    TextFlowTargetRepository textFlowTargetRepository;
    @Autowired
    TextFlowTargetHistoryRepository textFlowTargetHistoryRepository;
    @Autowired
    TranslationEditService translationEditService;
    @Autowired
    OfflineExportService offlineExportService;

    private final ObjectMapper json = new ObjectMapper();

    private FileSystem jimfs;
    private Path tmp;

    @AfterEach
    void tearDownFs() throws Exception {
        if (jimfs != null) {
            jimfs.close();
        }
    }

    /** In-memory working dir — all client file I/O is nio Path-based. */
    private Path inMemoryRoot() throws Exception {
        jimfs = Jimfs.newFileSystem(Configuration.unix());
        Path root = jimfs.getPath("/work");
        Files.createDirectories(root);
        return root;
    }

    @Test
    void propertiesSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT, VERSION);

        Path src = tmp.resolve("messages.properties");
        Files.writeString(src, "greeting=Hello\nbye=Goodbye\n");

        new PushCommand(pushOpts("source", "properties", PROJECT)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId(PROJECT, VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("Hello", "Goodbye");

        Files.delete(src);
        assertThat(Files.exists(src)).isFalse();

        new PullCommand(pullOpts("source", "properties", PROJECT)).run();

        assertThat(Files.exists(src)).isTrue();
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(src)) {
            p.load(in);
        }
        assertThat(p).as("pulled %s", Files.readString(src)).hasSize(2);
        assertThat(p.stringPropertyNames())
                .as("pulled keys must stay human-readable, not the resId hash: %s",
                        Files.readString(src))
                .containsExactlyInAnyOrder("greeting", "bye");
        assertThat(new java.util.HashSet<>(p.values()))
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @Test
    void gettextSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itgettext", VERSION);

        Path pot = tmp.resolve("messages.pot");
        Files.writeString(pot, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "greeting"
                msgstr ""

                msgid "bye"
                msgstr ""
                """);

        new PushCommand(pushOpts("source", "gettext", "itgettext")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itgettext", VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("greeting", "bye");

        Files.delete(pot);
        assertThat(Files.exists(pot)).isFalse();

        new PullCommand(pullOpts("source", "gettext", "itgettext")).run();

        assertThat(Files.exists(pot)).isTrue();
        String content = Files.readString(pot);
        assertThat(content).contains("greeting").contains("bye");
    }

    @Test
    void consuloSubFileRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulo", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Path anchor = libEn.resolve("my.Localize.yaml");
        Files.writeString(anchor, "greeting:\n    text: Hello\n");
        Path subDir = libEn.resolve("my.Localize");
        Files.createDirectories(subDir);
        Path sub = subDir.resolve("tip.html");
        Files.writeString(sub, "<b>Hi</b>");

        new PushCommand(pushOpts("source", "consulo", "itconsulo")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulo", VERSION, "my.Localize")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        HTextFlow rawSub = flows.stream()
                .filter(f -> f.getConsuloFileExt() != null).findFirst().orElseThrow();
        assertThat(rawSub.getConsuloFileExt()).isEqualTo("html");
        assertThat(rawSub.getContents().get(0)).isEqualTo("<b>Hi</b>");
        HTextFlow plain = flows.stream()
                .filter(f -> f.getConsuloFileExt() == null).findFirst().orElseThrow();
        assertThat(plain.getContents().get(0)).isEqualTo("Hello");

        Files.delete(sub);
        assertThat(Files.exists(sub)).isFalse();

        new PullCommand(pullOpts("source", "consulo", "itconsulo")).run();

        assertThat(Files.exists(sub)).as("sub-file recreated with same extension").isTrue();
        assertThat(Files.readString(sub)).isEqualTo("<b>Hi</b>");
        assertThat(Files.readString(anchor)).contains("greeting").contains("Hello");
    }

    @Test
    void propertiesTranslationLandsInDb() throws Exception {
        // Mirrors the `playground` layout: an en-US source bundle plus a
        // region-specific translation file.
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
    void rePushingTranslationsDoesNotDuplicateHistory() throws Exception {
        // Reproduces the "duplicate key (target_id, version_num)" crash. The
        // import wrote a history row by hand on every re-push of an already
        // translated string — even when nothing changed, so the target's
        // @Version stayed put. That leaves an orphan history row at the current
        // version; the next real edit's @PreUpdate listener then writes history
        // at the SAME version, violating the (target_id, version_num) natural id.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itdup", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itdup");

        // Re-push the SAME translation — nothing changes, so the version must
        // not advance and no history should be written.
        pushBoth("itdup");

        // Now change it and push again, exactly as a translator would.
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Salut\n");
        pushBoth("itdup");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itdup", VERSION, "messages")
                .orElseThrow();
        List<Long> ids = textFlowRepository.findByDocument(doc.getId()).stream()
                .map(HTextFlow::getId).toList();
        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(ids, new LocaleId("fr-FR"));
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .as("the re-pushed translation must be persisted")
                .containsExactly("Salut");

        // The crash was a duplicate (target_id, version_num). Production rejects
        // it; the test DB silently keeps both rows, so assert on the rows
        // directly — every version number must appear at most once.
        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(ids.get(0), new LocaleId("fr-FR"));
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    private void pushBoth(String proj) throws Exception {
        PushOptionsImpl push = pushOpts("both", "properties", proj);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        new PushCommand(push).run();
    }

    /** Pull (both) and return the lock the CLI wrote, like translate-sync does. */
    private VerbariaLock pullBothAndReadLock(String proj) throws Exception {
        PullOptionsImpl pull = pullOpts("both", "properties", proj);
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();
        return VerbariaLockReaderWriter.readOrNull(
                tmp.resolve("verbaria-lock.json"));
    }

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

    @Test
    void editedSourceIsPulledAfterApprovedBothPush() throws Exception {
        // Exact playground flow: push --approve --push-type=both, edit the
        // primary source Hello -> Hey in the editor, then pull into the SAME
        // dir (the local messages.properties still holds "Hello").
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itsrc2", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        PushOptionsImpl push = pushOpts("both", "properties", "itsrc2");
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        push.setApprove(true);
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrc2", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        translationEditService.updateSource(tfId, "Hey");

        // Pull source WITHOUT deleting the local file (it still has "Hello").
        PullOptionsImpl pull = pullOpts("source", "properties", "itsrc2");
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(tmp.resolve("messages.properties"))) {
            p.load(in);
        }
        assertThat(p.getProperty("greeting"))
                .as("edited source must be pulled into the existing file: %s",
                        Files.readString(tmp.resolve("messages.properties")))
                .isEqualTo("Hey");
    }

    @Test
    void editedSourceIsPulledFromDb() throws Exception {
        // Editing the primary/source text in the editor (updateSource) must be
        // reflected on a source pull.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itsrc", VERSION);

        Path src = tmp.resolve("messages.properties");
        Files.writeString(src, "greeting=Hello\n");
        PushOptionsImpl push = pushOpts("source", "properties", "itsrc");
        push.setIncludes("messages.properties");
        new PushCommand(push).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrc", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();

        translationEditService.updateSource(tfId, "Hey");

        Files.delete(src);
        PullOptionsImpl pull = pullOpts("source", "properties", "itsrc");
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(src)) {
            p.load(in);
        }
        assertThat(p.getProperty("greeting"))
                .as("edited source must be pulled from DB: %s", Files.readString(src))
                .isEqualTo("Hey");
    }

    @Test
    void translationPullKeepsHumanReadableKeys() throws Exception {
        // Source keys are hashed into the resId on push (original kept in
        // PotEntryHeader.context). Pulling with the source available (pull-type
        // both, as the playground does) must restore the readable keys, not emit
        // the 32-char hash. A trans-only pull has no source mapping and keeps the
        // hash — hence "both" here.
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
        pull.setIncludes("messages.properties");
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

    @Test
    void rejectedTranslationIsNotPulled() throws Exception {
        // A rejected translation must never be used: pulling it back must fall
        // back to source, not return the rejected text. Format-independent —
        // the server withholds it before any writer runs.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itrej", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itrej");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itrej", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        translationEditService.changeState(tfId, fr, ContentState.Rejected);

        Files.deleteIfExists(tmp.resolve("messages_fr_FR.properties"));
        PullOptionsImpl pull = pullOpts("trans", "properties", "itrej");
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();

        Path pulled = tmp.resolve("messages_fr_FR.properties");
        String content = Files.exists(pulled) ? Files.readString(pulled) : "";
        assertThat(content)
                .as("rejected translation must not appear in the pulled file")
                .doesNotContain("Bonjour");
    }

    @Test
    void rejectedTranslationIsExcludedFromExportRegardlessOfFormat()
            throws Exception {
        // Same rule via the offline export (gettext/PO output here) — proving
        // the policy lives in the shared resource builder, not per-format.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itrejexp", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itrejexp");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itrejexp", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        translationEditService.changeState(tfId, fr, ContentState.Rejected);

        OfflineExportService.Bundle bundle = offlineExportService
                .singleTranslatedDoc("itrejexp", VERSION, "messages", fr)
                .orElseThrow();
        String exported = new String(bundle.bytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(exported)
                .as("rejected translation must not appear in exported %s",
                        bundle.filename())
                .doesNotContain("Bonjour");
    }

    @Test
    void editorChangeStateApproveDoesNotDuplicateHistory() throws Exception {
        // The actual editor crash path: TranslationEditService.changeState(...,
        // Approved). It used to write a history row by hand on top of the one
        // the @PreUpdate listener writes, so the very next state change violated
        // the (target_id, version_num) natural id — the production
        // "duplicate key value violates unique constraint" 23505 error.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itstate", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itstate");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itstate", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        // Edit a couple of times to advance the @Version, mirroring a real
        // translator session before the approve that crashed at version 2.
        translationEditService.save(tfId, fr, "Salut");
        translationEditService.save(tfId, fr, "Coucou");

        ContentState state = translationEditService.changeState(
                tfId, fr, ContentState.Approved);
        assertThat(state)
                .as("approve must persist, not crash on duplicate history")
                .isEqualTo(ContentState.Approved);

        HTextFlowTarget target = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), fr).get(0);
        assertThat(target.getState()).isEqualTo(ContentState.Approved);
        assertThat(target.getContents()).containsExactly("Coucou");

        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, fr);
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorReApproveIsIdempotent() throws Exception {
        // Re-approving an already-approved row must be a harmless no-op (the UI
        // can leave the button enabled), not a duplicate-history crash.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itidem", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itidem");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itidem", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        translationEditService.changeState(tfId, fr, ContentState.Approved);
        translationEditService.changeState(tfId, fr, ContentState.Approved);
        ContentState state = translationEditService
                .changeState(tfId, fr, ContentState.Approved);

        assertThat(state).isEqualTo(ContentState.Approved);
        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, fr);
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    @Test
    void editorApproveAfterRePushPersistsAndDoesNotDuplicateHistory()
            throws Exception {
        // The exact playground report: the translation is pushed (and re-pushed,
        // which left an orphan history row at the target's current version while
        // its @Version stayed put), then a translator approves an edit in the
        // editor. The approve's @PreUpdate listener writes history at that same
        // version, hits the duplicate (target_id, version_num) constraint, the
        // transaction rolls back — so the approved text is never saved ("I can't
        // see Hey") and re-trying surfaces the duplicate-key error.
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itedit", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itedit");
        // Re-push the same translation: the buggy import leaves an orphan
        // history row at the current (unchanged) version.
        pushBoth("itedit");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itedit", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId())
                .get(0).getId();

        int approved = approve(tfId, "Hey");
        assertThat(approved)
                .as("editor approve must succeed, not 500 on duplicate history")
                .isEqualTo(200);

        List<HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("fr-FR"));
        assertThat(targets.stream().flatMap(t -> t.getContents().stream()))
                .as("the approved translation must actually be persisted")
                .containsExactly("Hey");

        List<HTextFlowTargetHistory> history = textFlowTargetHistoryRepository
                .findByTextFlowAndLocale(tfId, new LocaleId("fr-FR"));
        assertThat(history.stream().map(HTextFlowTargetHistory::getVersionNum))
                .as("history must not contain a duplicated version number")
                .doesNotHaveDuplicates();
    }

    private int approve(Long tfId, String content) throws Exception {
        Map<String, Object> body = Map.of(
                "id", tfId,
                "translations", List.of(content),
                "status", "Approved");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/rest/trans/fr-FR"))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }

    private static LocaleList frLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("fr-FR"));
        return ll;
    }

    private static LocaleList enUsLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("en-US"));
        return ll;
    }

    private PushOptionsImpl pushOpts(String pushType, String projectType, String proj)
            throws Exception {
        PushOptionsImpl o = new PushOptionsImpl();
        o.setUrl(URI.create("http://localhost:" + port + "/").toURL());
        o.setProj(proj);
        o.setProjectVersion(VERSION);
        o.setProjectType(projectType);
        o.setSrcDir(tmp);
        o.setTransDir(tmp);
        o.setUsername(USER);
        o.setKey(API_KEY);
        o.setBatchMode(true);
        o.setPushType(pushType);
        o.setIncludes("**");
        o.setLocaleMapList(enUsLocales());
        o.setProjectConfig(tmp.resolve("verbaria.json"));
        return o;
    }

    private PullOptionsImpl pullOpts(String pullType, String projectType, String proj)
            throws Exception {
        PullOptionsImpl o = new PullOptionsImpl();
        o.setUrl(URI.create("http://localhost:" + port + "/").toURL());
        o.setProj(proj);
        o.setProjectVersion(VERSION);
        o.setProjectType(projectType);
        o.setSrcDir(tmp);
        o.setTransDir(tmp);
        o.setUsername(USER);
        o.setKey(API_KEY);
        o.setBatchMode(true);
        o.setPullType(pullType);
        o.setIncludes("**");
        o.setLocaleMapList(enUsLocales());
        o.setProjectConfig(tmp.resolve("verbaria.json"));
        return o;
    }
}
