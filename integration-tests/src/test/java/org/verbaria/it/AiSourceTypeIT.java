package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.dto.TranslationSourceType;

class AiSourceTypeIT extends AbstractPushPullIT {

    @Test
    void aiSaveMarksMachineTransAndClearsOnHumanEdit() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itaitype", VERSION);

        Files.writeString(tmp.resolve("messages.properties"), "greeting=Hello\n");
        Files.writeString(tmp.resolve("messages_fr_FR.properties"),
                "greeting=Bonjour\n");
        pushBoth("itaitype");

        HDocument doc = documentRepository
                .findByVersionAndDocId("itaitype", VERSION, "messages")
                .orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");

        // AI-style save: the current target is marked MACHINE_TRANS / automated.
        translationEditService.save(tfId, fr, "IA", USER,
                TranslationSourceType.MACHINE_TRANS);
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            HTextFlowTarget t = textFlowTargetRepository
                    .findByTextFlowAndLocale(tfId, fr).orElseThrow();
            assertThat(t.getSourceType())
                    .isEqualTo(TranslationSourceType.MACHINE_TRANS);
            assertThat(t.getAutomatedEntry()).isTrue();
        });

        // Human edit must clear the AI marker on the current target.
        translationEditService.save(tfId, fr, "Salut", USER);
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            HTextFlowTarget cur = textFlowTargetRepository
                    .findByTextFlowAndLocale(tfId, fr).orElseThrow();
            assertThat(cur.getSourceType())
                    .as("a human edit must clear the AI marker")
                    .isNotEqualTo(TranslationSourceType.MACHINE_TRANS);
        });
    }
}
