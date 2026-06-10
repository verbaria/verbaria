package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

class SourceRePushIT extends AbstractPushPullIT {

    private static final LocaleId EN_US = new LocaleId("en-US");
    private static final String USER_A = "author-a-it";
    private static final String KEY_A = "aaaaaaaa1111aaaaaaaa1111aaaaaaaa";
    private static final String USER_B = "author-b-it";
    private static final String KEY_B = "bbbbbbbb2222bbbbbbbb2222bbbbbbbb";

    private static final String SOURCE_YAML = """
            greeting:
                text: 'Hello'
            farewell:
                text: 'Goodbye'
            """;

    private PushOptionsImpl srcPush(String projectType, String project,
            String user, String key) throws Exception {
        PushOptionsImpl o = pushOpts("source", projectType, project);
        o.setUsername(user);
        o.setKey(key);
        return o;
    }

    /**
     * A source push writes a {@code verbaria-lock.*} sync-state file into the
     * work dir; a subsequent push's {@code **} include glob would then try to
     * load it as a source document. Drop it before re-pushing.
     */
    private void clearLockFiles() throws Exception {
        Files.deleteIfExists(tmp.resolve("verbaria-lock.json"));
        Files.deleteIfExists(tmp.resolve("verbaria-lock.properties"));
    }

    private String authorOf(Long textFlowId) {
        return textFlowTargetRepository
                .findByTextFlowAndLocaleWithModifier(textFlowId, EN_US)
                .map(t -> t.getLastModifiedBy() == null
                        ? null : t.getLastModifiedBy().getName())
                .orElse(null);
    }

    @Test
    void identicalSourceRePushCreatesNoHistory() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureUser(USER_A, KEY_A);
        fixtures.ensureProject("itsrcrepush", VERSION);
        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");

        new PushCommand(srcPush("properties", "itsrcrepush", USER_A, KEY_A)).run();
        long historyAfterFirst = textFlowTargetHistoryRepository.count();

        clearLockFiles();
        new PushCommand(srcPush("properties", "itsrcrepush", USER_A, KEY_A)).run();
        long historyAfterSecond = textFlowTargetHistoryRepository.count();

        assertThat(historyAfterSecond)
                .as("identical source re-push must not create history rows")
                .isEqualTo(historyAfterFirst);
    }

    @Test
    void rePushByDifferentUserKeepsFirstAuthorAndHistory() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        // Commit 1 by an admin (source base stored Approved); commit 2 by a
        // non-admin (a plain push computes Translated). The stored state then
        // differs from what the second push computes — yet the content is
        // identical, so it must still be a no-op.
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureUser(USER_B, KEY_B);
        fixtures.ensureProject("itsrcrepushauthor", VERSION);
        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");

        // Commit 1 by the admin.
        new PushCommand(srcPush("properties", "itsrcrepushauthor", USER, API_KEY))
                .run();
        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrcrepushauthor", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        String authorAfterFirst = authorOf(tfId);
        long historyAfterFirst = textFlowTargetHistoryRepository.count();

        assertThat(authorAfterFirst).isEqualTo(USER);

        // Commit 2 by a different user, identical content.
        clearLockFiles();
        new PushCommand(srcPush("properties", "itsrcrepushauthor", USER_B, KEY_B))
                .run();

        assertThat(authorOf(tfId))
                .as("re-push by a different user must keep the previous author")
                .isEqualTo(USER);
        assertThat(textFlowTargetHistoryRepository.count())
                .as("re-push of identical content must not change history")
                .isEqualTo(historyAfterFirst);
    }

    @Test
    void rePushUpdatesConsuloNamesAndTypesWhenTextUnchanged() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureUser(USER_A, KEY_A);
        fixtures.ensureProject("itsrcrepushext", VERSION);
        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"), """
                greeting:
                    text: 'Hello'
                    names:
                        - userName
                    types:
                        - String
                """);
        new PushCommand(srcPush("consulo", "itsrcrepushext", USER_A, KEY_A)).run();

        // Same text, but the parameter metadata changed.
        clearLockFiles();
        Files.writeString(libEn.resolve("messages.yaml"), """
                greeting:
                    text: 'Hello'
                    names:
                        - userName
                        - count
                    types:
                        - String
                        - int
                """);
        new PushCommand(srcPush("consulo", "itsrcrepushext", USER_A, KEY_A)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itsrcrepushext", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        List<String> names = new TransactionTemplate(txManager).execute(s -> {
            HTextFlow tf = textFlowRepository.findById(tfId).orElseThrow();
            return extensionStore.get(tf, ConsuloSubFile.class)
                    .map(ConsuloSubFile::getParamNames).orElse(null);
        });

        assertThat(names)
                .as("re-push must update consulo names even when text is unchanged")
                .containsExactly("userName", "count");
    }

    @Test
    void identicalConsuloSourceRePushCreatesNoHistory() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureUser(USER_A, KEY_A);
        fixtures.ensureProject("itsrcrepushyaml", VERSION);
        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"), SOURCE_YAML);

        new PushCommand(srcPush("consulo", "itsrcrepushyaml", USER_A, KEY_A)).run();
        long historyAfterFirst = textFlowTargetHistoryRepository.count();

        clearLockFiles();
        new PushCommand(srcPush("consulo", "itsrcrepushyaml", USER_A, KEY_A)).run();
        long historyAfterSecond = textFlowTargetHistoryRepository.count();

        assertThat(historyAfterSecond)
                .as("identical consulo source re-push must not create history rows")
                .isEqualTo(historyAfterFirst);
    }
}
