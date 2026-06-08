package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import org.zanata.client.commands.push.PushCommand;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;

import org.verbaria.server.headless.service.OfflineExportService;

/**
 * Consulo {@code names}/{@code types} are source-side metadata describing a yaml
 * entry's placeholders. They are the same for every locale, so a translation
 * export must carry them through from the source — otherwise the translated yaml
 * loses the parameter declarations. (Source-sync is covered by
 * {@code SourceRoundTripIT}; this pins the translation side.)
 */
class ConsuloParamSyncIT extends AbstractPushPullIT {

    private static final String SOURCE_YAML = """
            greeting:
                text: 'Hello'
                names:
                    - userName
                types:
                    - String
            """;

    @Test
    void translationExportSyncsConsuloNamesAndTypes() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureLocale("fr-FR");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsuloexp", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Files.writeString(libEn.resolve("messages.yaml"), SOURCE_YAML);
        new PushCommand(pushOpts("source", "consulo", "itconsuloexp")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsuloexp", VERSION, "messages").orElseThrow();
        Long tfId = textFlowRepository.findByDocument(doc.getId()).get(0).getId();
        LocaleId fr = new LocaleId("fr-FR");
        translationEditService.save(tfId, fr, "Bonjour");

        OfflineExportService.Bundle bundle = offlineExportService
                .yamlForDoc("itconsuloexp", VERSION, "messages", fr).orElseThrow();
        String yaml = new String(bundle.bytes(), StandardCharsets.UTF_8);

        assertThat(yaml)
                .as("translated yaml export must keep the source names/types: %s", yaml)
                .contains("userName")
                .contains("String");
    }
}
