package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;

class CopyTransClientIT extends AbstractRestClientIT {

    // Copy-trans is not implemented by the one-API headless server (the bridge
    // answers 501 Not Implemented); there is no GET status endpoint.

    @Test
    void testStartCopyTrans() throws Exception {
        fixtures.ensureProject("rccopytrans", "master");
        assertThatThrownBy(() -> factory().getCopyTransClient()
                .startCopyTrans("rccopytrans", "master", "Authors"))
                .isInstanceOf(RestClientResponseException.class);
    }

    @Test
    void testGetCopyTransStatus() throws Exception {
        fixtures.ensureProject("rccopytrans", "master");
        assertThatThrownBy(() -> factory().getCopyTransClient()
                .getCopyTransStatus("rccopytrans", "master", "Authors"))
                .isInstanceOf(RestClientResponseException.class);
    }
}
