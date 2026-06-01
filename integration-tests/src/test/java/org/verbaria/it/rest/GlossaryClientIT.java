package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import org.zanata.common.LocaleId;

class GlossaryClientIT extends AbstractRestClientIT {

    @Test
    void testPut() throws Exception {
        // The one-API headless glossary is read-only (no push); the client call
        // surfaces the server's rejection.
        assertThatThrownBy(() -> factory().getGlossaryClient()
                .post(new ArrayList<>(), LocaleId.DE, "global/default"))
                .isInstanceOf(RestClientResponseException.class);
    }
}
