package org.zanata.adapter.consulo;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

class ConsuloTranslationFilesTest {

    private final ConsuloDocumentLayout layout = new ConsuloDocumentLayout();

    @Test
    void subFileEntryWritesExternalFileNotInlineYaml() throws Exception {
        Resource source = new Resource("messages");
        source.setLang(LocaleId.EN_US);
        source.getTextFlows().add(
                new TextFlow("greeting", LocaleId.EN_US, "Hello"));
        TextFlow file = new TextFlow("docs.readme", LocaleId.EN_US,
                "<b>source</b>");
        file.getExtensions(true).add(new ConsuloSubFile("html"));
        source.getTextFlows().add(file);

        TranslationsResource trans = new TranslationsResource();
        trans.getTextFlowTargets().add(target("greeting", "Привет"));
        trans.getTextFlowTargets().add(target("docs.readme", "<b>перевод</b>"));

        Map<String, byte[]> files =
                layout.writeTranslationFiles(source, trans, "ru-RU");

        // The raw sub-file entry becomes its own file with the translation.
        assertThat(files).containsKey("ru_RU/messages/docs/readme.html");
        assertThat(new String(files.get("ru_RU/messages/docs/readme.html"), UTF_8))
                .isEqualTo("<b>перевод</b>");

        // The yaml carries only the inline entry, not the sub-file content.
        String yaml = new String(files.get("ru_RU/messages.yaml"), UTF_8);
        assertThat(yaml).contains("Привет");
        assertThat(yaml).doesNotContain("docs/readme");
        assertThat(yaml).doesNotContain("перевод");
    }

    @Test
    void sourceAndTranslationUseSameLayout() throws Exception {
        Resource source = new Resource("messages");
        source.setLang(LocaleId.EN_US);
        TextFlow file = new TextFlow("docs.readme", LocaleId.EN_US,
                "<b>source</b>");
        file.getExtensions(true).add(new ConsuloSubFile("html"));
        source.getTextFlows().add(file);

        Map<String, byte[]> sourceFiles =
                layout.writeSourceFiles(source, "en-US");

        TranslationsResource trans = new TranslationsResource();
        trans.getTextFlowTargets().add(target("docs.readme", "<b>source</b>"));
        Map<String, byte[]> transFiles =
                layout.writeTranslationFiles(source, trans, "en-US");

        // Same entry, same locale → identical file layout and content.
        assertThat(transFiles.keySet()).isEqualTo(sourceFiles.keySet());
        assertThat(new String(transFiles.get("en_US/messages/docs/readme.html"), UTF_8))
                .isEqualTo(new String(sourceFiles.get("en_US/messages/docs/readme.html"), UTF_8));
    }

    private static TextFlowTarget target(String resId, String content) {
        TextFlowTarget t = new TextFlowTarget(resId);
        t.setContents(content);
        t.setState(ContentState.Translated);
        return t;
    }
}
