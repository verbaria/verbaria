/*
 * Copyright Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.service.tm.merge;

import javax.annotation.Nullable;

import org.zanata.config.TMFuzzyBandsConfig;

import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public class TMBandDefsProducer {
    /**
     * Produces a full set of TM bands, given the application configuration for the Fuzzy bands as a String.
     */
    @Produces
    @Dependent
    public TMBandDefs produce(@TMFuzzyBandsConfig @Nullable String config) {
        // try to keep the default here in sync with jsf.config.tmfuzzybands.placeholder in messages.properties
        String configToUse = config != null ? config : "80 90";
        return new TMBandDefs(
                TMBandDefs.createTMBands(TMBandDefs.parseBands(configToUse)));
    }
}
