package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.ProcessStatus;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

class AsyncProcessClientIT extends AbstractRestClientIT {

    private static final Set<String> EXT = Set.of("gettext", "comment");

    private Resource source() {
        Resource doc = new Resource("message");
        doc.setLang(LocaleId.EN_US);
        doc.getTextFlows().add(new TextFlow("hello", LocaleId.EN_US, "world"));
        return doc;
    }

    @Test
    void testStartSourceDocCreationOrUpdate() throws Exception {
        fixtures.ensureProject("rcasyncsrc", "master");
        ProcessStatus status = factory().getAsyncProcessClient()
                .startSourceDocCreationOrUpdateWithDocId("rcasyncsrc", "master",
                        source(), EXT, "message", false);
        assertThat(status.getStatusCode()).isNotNull();
    }

    @Test
    void testStartTranslatedDocCreationOrUpdate() throws Exception {
        fixtures.ensureLocale("de");
        fixtures.ensureProject("rcasynctrans", "master");
        factory().getAsyncProcessClient().startSourceDocCreationOrUpdateWithDocId(
                "rcasynctrans", "master", source(), EXT, "message", false);

        TranslationsResource tr = new TranslationsResource();
        TextFlowTarget target = new TextFlowTarget("hello");
        target.setContents("welt");
        target.setState(ContentState.Translated);
        tr.getTextFlowTargets().add(target);

        ProcessStatus status = factory().getAsyncProcessClient()
                .startTranslatedDocCreationOrUpdateWithDocId("rcasynctrans",
                        "master", LocaleId.DE, tr, "message", EXT, "auto", false, false);
        assertThat(status.getStatusCode()).isNotNull();
    }

    @Test
    void testGetProcessStatus() throws Exception {
        ProcessStatus status = factory().getAsyncProcessClient()
                .getProcessStatus("a");
        assertThat(status.getStatusCode()).isNotNull();
    }
}
