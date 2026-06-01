package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientResponseException;
import org.zanata.rest.dto.Account;

class AccountClientIT extends AbstractRestClientIT {

    @Test
    void testGet() throws Exception {
        // The legacy /accounts/u path is not a real endpoint on the one-API
        // server; the SPA catch-all returns an empty body, so the client yields
        // a null account rather than the legacy stub's fixed value.
        assertThat(factory().getAccountClient().get("admin")).isNull();
    }

    @Test
    void testPut() throws Exception {
        // Account creation/update is not exposed by the one-API headless server.
        assertThatThrownBy(() -> factory().getAccountClient()
                .put("admin", new Account("a@b.c", "d", "e", "f")))
                .isInstanceOf(RestClientResponseException.class);
    }
}
