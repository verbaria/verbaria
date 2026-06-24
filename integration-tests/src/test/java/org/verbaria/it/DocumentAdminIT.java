package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlowTarget;
import org.verbaria.server.headless.service.DocumentAdminService;

/**
 * Admin document maintenance: soft-delete/restore toggles the obsolete flag,
 * and hard-delete purges the document with all its text flows, targets and
 * history without tripping a foreign key.
 */
class DocumentAdminIT extends AbstractPushPullIT {

    @Autowired
    DocumentAdminService documentAdminService;

    private void pushSource(String proj) throws Exception {
        PushOptionsImpl o = pushOpts("source", "properties", proj);
        o.setIncludes("messages.properties");
        new PushCommand(o).run();
    }

    @Test
    void hardDeleteRemovesDocumentAndAllChildren() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itdocpurge", VERSION);
        Files.writeString(tmp.resolve("messages.properties"),
                "greeting=Hello\nbye=Goodbye\n");

        pushSource("itdocpurge");
        HDocument doc = documentRepository
                .findByVersionAndDocId("itdocpurge", VERSION, "messages")
                .orElseThrow();
        Long docId = doc.getId();
        Long tfId = textFlowRepository.findByDocument(docId).get(0).getId();

        // Create an fr-FR target, then change it so a history row is snapshotted.
        assertThat(approve(tfId, "Bonjour")).isEqualTo(200);
        assertThat(approve(tfId, "Salut")).isEqualTo(200);
        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("fr-FR")))
                .as("precondition: an fr-FR target exists")
                .hasSize(1);

        // Purge.
        documentAdminService.hardDelete("itdocpurge", VERSION, "messages");

        assertThat(documentRepository
                .findAnyByVersionAndDocId("itdocpurge", VERSION, "messages"))
                .as("document row must be gone")
                .isEmpty();
        assertThat(textFlowRepository.findAllByDocumentIncludingObsolete(docId))
                .as("all text flows must be gone")
                .isEmpty();
        assertThat(textFlowTargetRepository
                .findByTextFlowIdsAndLocale(List.of(tfId), new LocaleId("fr-FR")))
                .as("the doc's targets must be gone")
                .isEmpty();
    }

    @Test
    void softDeleteHidesAndRestoreBringsBack() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itdocsoft", VERSION);
        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");

        pushSource("itdocsoft");
        assertThat(documentRepository.countByVersion("itdocsoft", VERSION))
                .isEqualTo(1);

        documentAdminService.softDelete("itdocsoft", VERSION, "messages");
        assertThat(documentRepository.countByVersion("itdocsoft", VERSION))
                .as("soft-deleted doc drops out of the live count")
                .isEqualTo(0);
        assertThat(documentRepository
                .findAnyByVersionAndDocId("itdocsoft", VERSION, "messages"))
                .as("but the row still exists")
                .isPresent();

        documentAdminService.restore("itdocsoft", VERSION, "messages");
        assertThat(documentRepository.countByVersion("itdocsoft", VERSION))
                .as("restore brings it back into the live count")
                .isEqualTo(1);
    }
}
