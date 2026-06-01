package org.zanata.adapter.xliff;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.FileNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zanata.adapter.xliff.XliffCommon.ValidationType;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

public class XliffReaderTest {
    private static final String TEST_DIR = "src/test/resources/";
    private static final String DOC_NAME = "StringResource_en_US.xml";
    private XliffReader reader;

    @BeforeEach
    public void resetReader() {
        reader = new XliffReader();
    }

    @Test
    public void extractTemplateSizeTest() throws Exception {
        Resource doc = getTemplateDoc();

        assertThat(doc.getName()).isEqualTo(DOC_NAME);
        assertThat(doc.getTextFlows()).hasSize(7);
    }

    @Test
    public void templateFirstAndSecondLastTextFlowTest()
            throws Exception {
        Resource doc = getTemplateDoc();

        TextFlow firstTextFlow = doc.getTextFlows().get(0);
        TextFlow lastTextFlow =
                doc.getTextFlows().get(doc.getTextFlows().size() - 2);

        assertThat(firstTextFlow.getContents())
                .isEqualTo(asList("Translation Unit 1"));
        assertThat(lastTextFlow.getContents())
                .isEqualTo(asList("Translation Unit 4 (4 < 5 & 4 > 3)"));
    }

    @Test
    public void extractTargetSizeTest() throws Exception {
        File fileTarget = new File(TEST_DIR, "/StringResource_de.xml");
        TranslationsResource tr = reader.extractTarget(fileTarget.toPath());
        // the file contains 4 trans-units, but one has no target element
        assertThat(tr.getTextFlowTargets()).hasSize(4);
    }

    @Test
    public void targetFirstAndLastTextFlowTest() throws Exception {
        File fileTarget = new File(TEST_DIR, "/StringResource_de.xml");
        TranslationsResource tr = reader.extractTarget(fileTarget.toPath());

        TextFlowTarget firstTextFlow = tr.getTextFlowTargets().get(0);
        TextFlowTarget lastTextFlow =
                tr.getTextFlowTargets().get(tr.getTextFlowTargets().size() - 2);

        assertThat(firstTextFlow.getContents())
                .isEqualTo(asList("Translation 1"));
        assertThat(lastTextFlow.getContents())
                .isEqualTo(asList("Translation 4 (4 < 5 & 4 > 3)"));
    }

    @Test
    public void leadingEndingWhiteSpaceTargetTest()
            throws Exception {
        File fileTarget = new File(TEST_DIR, "/StringResource_de.xml");
        TranslationsResource tr = reader.extractTarget(fileTarget.toPath());

        TextFlowTarget lastTextFlow =
                tr.getTextFlowTargets().get(tr.getTextFlowTargets().size() - 1);
        assertThat(lastTextFlow.getContents())
                .isEqualTo(asList(" Leading and trailing white space "));
        assertThat(lastTextFlow.getContents())
                .isNotEqualTo(asList("Leading and trailing white space"));
        assertThat(lastTextFlow.getContents())
                .isNotEqualTo(asList(" Leading and trailing white space"));
        assertThat(lastTextFlow.getContents())
                .isNotEqualTo(asList("Leading and trailing white space "));
    }

    @Test
    public void leadingEndingWhiteSpaceSourceTest()
            throws Exception {
        File fileTarget = new File(TEST_DIR, "/StringResource_de.xml");
        Resource resource =
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.XSD.toString());

        TextFlow tf =
                resource.getTextFlows().get(resource.getTextFlows().size() - 1);
        assertThat(tf.getContents())
                .isEqualTo(asList(" Translation Unit 5 (4 < 5 & 4 > 3) "));
        assertThat(tf.getContents())
                .isNotEqualTo(asList("Translation Unit 5 (4 < 5 & 4 > 3)"));
        assertThat(tf.getContents())
                .isNotEqualTo(asList(" Translation Unit 5 (4 < 5 & 4 > 3)"));
        assertThat(tf.getContents())
                .isNotEqualTo(asList("Translation Unit 5 (4 < 5 & 4 > 3) "));
    }

    @Test
    public void invalidSourceContentElementTest() throws Exception {
        // expect RuntimeException with tu:transunit2 - source
        File fileTarget =
                new File(TEST_DIR, "/StringResource_source_invalid.xml");
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.CONTENT.toString()));
        assertThat(e.getMessage()).contains("br is not legal");
    }

    @Test
    public void invalidSourceContentElementTest2() throws Exception {
        // expect RuntimeException with tu:transunit2 - source
        File fileTarget =
                new File(TEST_DIR, "/StringResource_source_invalid.xml");
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.XSD.toString()));
        assertThat(e.getMessage()).contains("Invalid XLIFF file format");
    }

    @Test
    public
            void unsupportedSourceContentElementTest()
                    throws Exception {
        // expect RuntimeException with tu:transunit2 - source
        File fileTarget =
                new File(TEST_DIR, "/StringResource_source_unsupported.xml");
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.CONTENT.toString()));
        assertThat(e.getMessage())
                .contains("does not support elements inside source: g");
    }

    @Test
    public void unsupportedSourceContentElementTest2()
            throws Exception {
        // expect RuntimeException with tu:transunit2 - source
        File fileTarget =
                new File(TEST_DIR, "/StringResource_source_unsupported.xml");
        RuntimeException e = assertThrows(RuntimeException.class, () ->
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.XSD.toString()));
        assertThat(e.getMessage()).contains("Invalid XLIFF file format");
    }

    @Test
    public void invalidTargetContentElementTest() throws Exception {
        // expect RuntimeException with tu:transunit1 - target
        File fileTarget =
                new File(TEST_DIR, "/StringResource_target_invalid.xml");
        Resource resource =
                reader.extractTemplate(fileTarget.toPath(), LocaleId.EN_US, null,
                        ValidationType.CONTENT.toString());
        assert resource != null;
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> reader.extractTarget(fileTarget.toPath()));
        assertThat(e.getMessage()).contains("Invalid XLIFF: "
                + "anIllegalTag is not legal inside target");
    }

    private Resource getTemplateDoc() throws Exception {
        File file = new File(TEST_DIR, File.separator + DOC_NAME);
        return reader.extractTemplate(file.toPath(), LocaleId.EN_US, DOC_NAME,
                ValidationType.XSD.toString());
    }
}
