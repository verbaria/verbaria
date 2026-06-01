package org.verbaria.it.rest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.zanata.rest.dto.VersionInfo;

class RestClientFactoryIT extends AbstractRestClientIT {

    @Test
    void testGetServerVersionInfo() throws Exception {
        VersionInfo serverVersionInfo = factory().getServerVersionInfo();
        assertThat(serverVersionInfo.getVersionNo()).isNotBlank();
    }
}
