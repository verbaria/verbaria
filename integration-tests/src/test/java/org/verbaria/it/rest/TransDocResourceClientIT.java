package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

class TransDocResourceClientIT extends AbstractRestClientIT {

    @Test
    void testGetTranslations() throws Exception {
        fixtures.ensureLocale("de");
        fixtures.ensureProject("rctrans", "master");
        Set<String> ext = Set.of("gettext", "comment");

        Resource doc = new Resource("doc");
        doc.setLang(LocaleId.EN_US);
        doc.getTextFlows().add(new TextFlow("hello", LocaleId.EN_US, "world"));
        factory().getAsyncProcessClient().startSourceDocCreationOrUpdateWithDocId(
                "rctrans", "master", doc, ext, "doc", false);

        // No translation exists for the locale, so the server answers 404 —
        // exactly the "no translations in locale X" signal PullCommand relies on.
        assertThatThrownBy(() -> factory()
                .getTransDocResourceClient("rctrans", "master")
                .getTranslations("doc", LocaleId.DE, ext, true, null))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
