package org.zanata.adapter.consulo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

class ConsuloMnemonicTest {

    private final ConsuloReader reader = new ConsuloReader();

    private Resource read(String yaml) {
        return reader.extractTemplate("messages",
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }

    private ConsuloSubFile sub(TextFlow tf) {
        return tf.getExtensions() == null ? null
                : tf.getExtensions().findByType(ConsuloSubFile.class);
    }

    @Test
    void firstOccurrenceMnemonicNeedsNoIndex() {
        Resource r = read("k:\n    text: '&File'\n");
        TextFlow tf = r.getTextFlows().get(0);
        assertThat(tf.getContents()).containsExactly("File");
        ConsuloSubFile s = sub(tf);
        assertThat(s).isNotNull();
        assertThat(s.getMnemonic()).isEqualTo("F");
        assertThat(s.getMnemonicIndex()).isNull();
    }

    @Test
    void laterOccurrenceMnemonicCarriesOneBasedIndex() {
        Resource r = read("k:\n    text: 'For &File'\n");
        TextFlow tf = r.getTextFlows().get(0);
        assertThat(tf.getContents()).containsExactly("For File");
        ConsuloSubFile s = sub(tf);
        assertThat(s.getMnemonic()).isEqualTo("F");
        assertThat(s.getMnemonicIndex()).isEqualTo(5);
    }

    @Test
    void explicitMnemonicFieldsAreHonoured() {
        Resource r = read("k:\n    text: For File\n    mnemonic: F\n"
                + "    mnemonicIndex: 5\n");
        TextFlow tf = r.getTextFlows().get(0);
        assertThat(tf.getContents()).containsExactly("For File");
        ConsuloSubFile s = sub(tf);
        assertThat(s.getMnemonic()).isEqualTo("F");
        assertThat(s.getMnemonicIndex()).isEqualTo(5);
    }

    @Test
    void plainTextHasNoMnemonic() {
        Resource r = read("k:\n    text: File\n");
        assertThat(sub(r.getTextFlows().get(0))).isNull();
    }

    @Test
    void roundTripWritesMnemonicArgs() throws Exception {
        Resource r = read("k:\n    text: 'For &File'\n");
        byte[] out = new ConsuloDocumentLayout().writeSource(r);
        String yaml = new String(out, StandardCharsets.UTF_8);
        assertThat(yaml).contains("text: For File")
                .contains("mnemonic: F")
                .contains("mnemonicIndex: 5");
    }
}
