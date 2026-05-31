package org.zanata.client.commands.push;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.zanata.common.ContentState;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the client side of {@code push --approve}: every <em>non-empty</em>
 * translation target is flipped to {@link ContentState#Approved}, while empty
 * targets are left untouched. (The server independently downgrades Approved to
 * Translated for non-admin callers — that guard lives in DocumentImportService
 * and is not exercised here.)
 */
@RunWith(JUnit4.class)
public class PushApproveTest {

    private static TextFlowTarget target(String resId, ContentState state,
            String... contents) {
        TextFlowTarget t = new TextFlowTarget(resId);
        t.setContents(Arrays.asList(contents));
        t.setState(state);
        return t;
    }

    @Test
    public void approvesOnlyNonEmptyTargets() {
        TranslationsResource tr = new TranslationsResource();
        TextFlowTarget filled = target("r1", ContentState.Translated, "Добавить");
        TextFlowTarget blank = target("r2", ContentState.New, "");
        TextFlowTarget noContent = target("r3", ContentState.New);
        tr.getTextFlowTargets().add(filled);
        tr.getTextFlowTargets().add(blank);
        tr.getTextFlowTargets().add(noContent);

        PushCommand.markApproved(tr);

        // Real translation → approved.
        assertThat(filled.getState()).isEqualTo(ContentState.Approved);
        // Empty / contentless targets are not approved.
        assertThat(blank.getState()).isEqualTo(ContentState.New);
        assertThat(noContent.getState()).isEqualTo(ContentState.New);
    }

    @Test
    public void leavesAlreadyApprovedAlone() {
        TranslationsResource tr = new TranslationsResource();
        TextFlowTarget t = target("r1", ContentState.Approved, "Готово");
        tr.getTextFlowTargets().add(t);

        PushCommand.markApproved(tr);

        assertThat(t.getState()).isEqualTo(ContentState.Approved);
    }

    @Test
    public void noTargetsIsANoOp() {
        TranslationsResource tr = new TranslationsResource();
        // A source-only push produces no targets — nothing to approve.
        PushCommand.markApproved(tr);
        assertThat(tr.getTextFlowTargets()).isEmpty();
    }
}
