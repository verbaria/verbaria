package org.zanata.adapter.properties;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * A pulled translation must omit untranslated entries entirely, not write them
 * as empty {@code key=} lines — empty values would override the source/parent
 * bundle's fallback (e.g. blank i18n strings in the UI).
 */
class PropertiesTranslationOmitTest {

    private final Utf8PropertiesDocumentLayout layout =
            new Utf8PropertiesDocumentLayout();

    @Test
    void untranslatedEntriesAreOmittedNotWrittenEmpty() throws Exception {
        Resource source = new Resource("messages");
        source.setLang(LocaleId.EN_US);
        source.getTextFlows()
                .add(new TextFlow("greeting", LocaleId.EN_US, "Hello"));
        source.getTextFlows()
                .add(new TextFlow("farewell", LocaleId.EN_US, "Goodbye"));

        TranslationsResource trans = new TranslationsResource();
        TextFlowTarget greeting = new TextFlowTarget("greeting");
        greeting.setContents("Bonjour");
        greeting.setState(ContentState.Translated);
        trans.getTextFlowTargets().add(greeting);

        byte[] out = layout.writeTranslation(source, trans, "fr");

        Properties props = new Properties();
        props.load(new InputStreamReader(new ByteArrayInputStream(out), UTF_8));

        assertThat(props.getProperty("greeting")).isEqualTo("Bonjour");
        assertThat(props.containsKey("farewell"))
                .as("the untranslated entry must be omitted, not written empty")
                .isFalse();
    }
}
