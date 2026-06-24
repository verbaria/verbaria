package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.common.MessageEvaluateType;
import org.zanata.model.HProject;

/**
 * The source URL template (possibly inherited from a parent) expands its
 * {@code $PROJECT_ID$} / {@code $PROJECT_NAME$} macros using each child's own
 * identity — so one parent template serves the whole family.
 */
class ProjectSourceUrlMacroTest {

    @Test
    void childExpandsInheritedTemplateWithOwnIdentity() {
        HProject parent = new HProject();
        parent.setSlug("consulo");
        parent.setName("Consulo");
        parent.setSourceViewURL("http://github.com/consulo/$PROJECT_ID$");

        HProject child = new HProject();
        child.setSlug("consulo-java");
        child.setName("Consulo Java");
        child.setParentProject(parent);

        assertThat(child.getResolvedSourceViewURL())
                .isEqualTo("http://github.com/consulo/consulo-java");
        assertThat(parent.getResolvedSourceViewURL())
                .isEqualTo("http://github.com/consulo/consulo");
    }

    @Test
    void ownUrlWinsAndProjectNameMacroExpands() {
        HProject parent = new HProject();
        parent.setSlug("consulo");
        parent.setSourceViewURL("http://parent/$PROJECT_ID$");

        HProject child = new HProject();
        child.setSlug("consulo-css");
        child.setName("CSS");
        child.setParentProject(parent);
        child.setSourceViewURL("http://own/$PROJECT_NAME$");

        assertThat(child.getResolvedSourceViewURL()).isEqualTo("http://own/CSS");
    }

    @Test
    void noTemplateAnywhereResolvesToNull() {
        HProject child = new HProject();
        child.setSlug("x");
        assertThat(child.getResolvedSourceViewURL()).isNull();
    }

    @Test
    void messageFormatIsInheritedFromParentUnlessOwnSet() {
        HProject parent = new HProject();
        parent.setSlug("consulo");
        parent.setMessageEvaluateType(MessageEvaluateType.JAVA_MESSAGE_FORMAT);

        HProject child = new HProject();
        child.setSlug("consulo-java");
        child.setParentProject(parent);

        assertThat(child.getEffectiveMessageEvaluateType())
                .as("child with no own message format inherits the parent's")
                .isEqualTo(MessageEvaluateType.JAVA_MESSAGE_FORMAT);

        child.setMessageEvaluateType(MessageEvaluateType.NONE);
        assertThat(child.getEffectiveMessageEvaluateType())
                .as("a child's own setting wins over the parent")
                .isEqualTo(MessageEvaluateType.NONE);

        HProject standalone = new HProject();
        standalone.setSlug("y");
        assertThat(standalone.getEffectiveMessageEvaluateType())
                .as("no parent, no own setting -> NONE")
                .isEqualTo(MessageEvaluateType.NONE);
    }
}
