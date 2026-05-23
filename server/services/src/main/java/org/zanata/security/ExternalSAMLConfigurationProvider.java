/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.security;

import org.picketlink.common.ErrorCodes;
import org.picketlink.common.exceptions.ConfigurationException;
import org.picketlink.common.exceptions.ProcessingException;
import org.picketlink.config.federation.IDPType;
import org.picketlink.config.federation.PicketLinkType;
import org.picketlink.config.federation.SPType;
import org.picketlink.identity.federation.web.config.AbstractSAMLConfigurationProvider;
import org.picketlink.identity.federation.web.util.ConfigurationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;

/**
 * This file is responsible to load the picketlink configuration file (path
 * given by system property).
 *
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class ExternalSAMLConfigurationProvider
        extends AbstractSAMLConfigurationProvider {

    private static final Logger log =
            LoggerFactory.getLogger(ExternalSAMLConfigurationProvider.class);

    private static final String CONFIG_FILE =
            System.getProperty("picketlink.file");

    // Returns the picketlink configuration file path including protocol.
    // Lazy holder pattern, equivalent to the Kotlin `by lazy` delegate.
    private static final class ConfigurationFilePathHolder {
        static final URL URL_VALUE = computeUrl();

        private static URL computeUrl() {
            try {
                return Paths.get(CONFIG_FILE).toUri().toURL();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static URL getConfigurationFilePath() {
        return ConfigurationFilePathHolder.URL_VALUE;
    }

    @Override
    public IDPType getIDPConfiguration() throws ProcessingException {
        throw new RuntimeException(ErrorCodes.ILLEGAL_METHOD_CALLED);
    }

    @Override
    public SPType getSPConfiguration() throws ProcessingException {
        try (InputStream inputStream = readConfigurationFile()) {
            if (inputStream == null) {
                return null;
            }
            return ConfigurationUtil.getSPConfiguration(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not load SP configuration: "
                            + getConfigurationFilePath(),
                    e);
        }
    }

    @Override
    public PicketLinkType getPicketLinkConfiguration()
            throws ProcessingException {
        try (InputStream inputStream = readConfigurationFile()) {
            if (inputStream == null) {
                return null;
            }
            return ConfigurationUtil.getConfiguration(inputStream);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Could not load PicketLink configuration: "
                            + getConfigurationFilePath(),
                    e);
        }
    }

    private static boolean isFileExists(String filePath) {
        File file = new File(filePath);
        return file.exists() && file.canRead();
    }

    private static InputStream readConfigurationFile()
            throws ConfigurationException {
        if (CONFIG_FILE == null || !isFileExists(CONFIG_FILE)) {
            log.info("picketlink.xml can not be found: {}", CONFIG_FILE);
            return null;
        }

        try {
            URL configurationFileURL = Thread.currentThread()
                    .getContextClassLoader().getResource(CONFIG_FILE);
            if (configurationFileURL == null) {
                configurationFileURL = getConfigurationFilePath();
            }
            return configurationFileURL.openStream();
        } catch (Exception e) {
            throw new RuntimeException(
                    "The file could not be loaded: " + CONFIG_FILE, e);
        }
    }
}
