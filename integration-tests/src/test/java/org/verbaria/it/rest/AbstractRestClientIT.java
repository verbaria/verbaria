package org.verbaria.it.rest;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.verbaria.it.ItApplication;
import org.verbaria.it.ItFixtures;
import org.zanata.rest.client.RestClientFactory;
import org.zanata.util.VersionUtility;

/**
 * Shared harness: each rest-client test runs against the real headless server
 * (the one API) instead of the deleted in-memory stub-server. The default url
 * prefix is {@code rest/}, exactly where the CLI bridge is mounted.
 */
@SpringBootTest(classes = ItApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractRestClientIT {

    protected static final String USER = "admin";
    protected static final String API_KEY =
            "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    protected int port;

    @Autowired
    protected ItFixtures fixtures;

    @BeforeEach
    void ensureAdmin() {
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
    }

    protected RestClientFactory factory() throws Exception {
        // Derive the client API version the same way the real CLI does, from the
        // zanata-common-api jar manifest, instead of hardcoding it per release.
        return new RestClientFactory(
                new URI("http://localhost:" + port + "/"), USER, API_KEY,
                VersionUtility.getAPIVersionInfo(), true, true);
    }
}
