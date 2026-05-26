/*
 * Copyright 2014, Red Hat, Inc. and individual contributors
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

package org.zanata.rest.client;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.zanata.rest.MediaTypes;
import org.zanata.rest.RestConstant;
import org.zanata.rest.dto.VersionInfo;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class RestClientFactory implements Serializable {
    private static final Logger log =
            LoggerFactory.getLogger(RestClientFactory.class);
    private static final long serialVersionUID = -276490112687360482L;
    private String serverVersion;
    private String clientVersion;
    private VersionInfo clientApiVersion;

    @SuppressFBWarnings(value = "SE_BAD_FIELD")
    private transient RestClient springRestClient;
    private URI baseURI;

    // for use by InitCommand
    protected RestClientFactory() {
    }

    public RestClientFactory(URI base, String username, String apiKey,
            VersionInfo clientApiVersion, boolean logHttp,
            boolean sslCertDisabled) {
        baseURI = base;
        this.clientApiVersion = clientApiVersion;
        clientVersion = clientApiVersion.getVersionNo();
        this.springRestClient = buildSpringRestClient(username, apiKey,
                clientVersion);
    }

    private RestClient buildSpringRestClient(String username, String apiKey,
            String clientVersion) {
        return RestClient.builder()
                .baseUrl(getBaseUri().toString())
                .defaultHeader(RestConstant.HEADER_USERNAME,
                        username == null ? "" : username)
                .defaultHeader(RestConstant.HEADER_API_KEY,
                        apiKey == null ? "" : apiKey)
                .defaultHeader(RestConstant.HEADER_VERSION_NO,
                        clientVersion == null ? "" : clientVersion)
                .defaultStatusHandler(HttpStatusCode::isError,
                        (request, response) -> {
                            int status = response.getStatusCode().value();
                            if (status == 503) {
                                throw HttpServerErrorException.create(
                                        response.getStatusCode(),
                                        "Service is currently unavailable. " +
                                                "Please check outage notification or try again later.",
                                        response.getHeaders(), null, null);
                            }
                            if (status >= 500) {
                                throw HttpServerErrorException.create(
                                        response.getStatusCode(),
                                        response.getStatusText(),
                                        response.getHeaders(), null, null);
                            }
                            throw HttpClientErrorException.create(
                                    response.getStatusCode(),
                                    response.getStatusText(),
                                    response.getHeaders(), null, null);
                        })
                .build();
    }

    public VersionInfo getServerVersionInfo() {
        return springRestClient.get()
                .uri("version")
                .accept(org.springframework.http.MediaType.parseMediaType(
                        MediaTypes.APPLICATION_ZANATA_VERSION_JSON))
                .retrieve()
                .body(VersionInfo.class);
    }

    public void performVersionCheck() {
        clientVersion = clientApiVersion.getVersionNo();
        String clientScm = clientApiVersion.getScmDescribe();

        VersionInfo serverVersionInfo = getServerVersionInfo();
        serverVersion = serverVersionInfo.getVersionNo();
        String serverScm = serverVersionInfo.getScmDescribe();
        log.info("client API version: {}, server API version: {}",
                clientVersion, serverVersion);
        warnMismatchAPIVersion(clientScm, serverScm);
    }

    private void warnMismatchAPIVersion(String clientScm, String serverScm) {
        if (!serverVersion.equals(clientVersion)) {
            log.warn("client API version is {}, but server API version is {}",
                    clientVersion, serverVersion);
        } else if (serverVersion.contains(RestConstant.SNAPSHOT_VERSION)
                && !serverScm.equalsIgnoreCase(clientScm)) {
            log.warn(
                    "client API SCM id is {}, but server API SCM id is {}",
                    clientScm, serverScm);
        }
    }

    private URL getBaseUrl() {
        try {
            return new URL(fixBase(baseURI).toString() + getUrlPrefix());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    protected URI getBaseUri() {
        try {
            return getBaseUrl().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static URI fixBase(URI base) {
        if (base != null) {
            String baseString = base.toString();
            if (!baseString.endsWith("/")) {
                try {
                    URI result = new URI(baseString + "/");
                    log.warn("Appending '/' to base URL '{}': using '{}'",
                            baseString, result);
                    return result;
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return base;
    }

    protected String getUrlPrefix() {
        return "rest/";
    }

    protected RestClient getSpringRestClient() {
        return springRestClient;
    }

    /**
     * Compares a given version identifier with the server version.
     *
     * @param version
     *            The version to against which to compare the server version.
     * @return A positive integer if the server version is greater than the
     *         given version. A negative integer if the server version is less
     *         than the given version. 0 if both versions are the same.
     */
    public int compareToServerVersion(String version) {
        return compareVersions(serverVersion, version);
    }

    /**
     * Compares two Maven-style version strings. Numeric segments are compared
     * numerically, others lexicographically. A version with a -SNAPSHOT (or
     * any qualifier) suffix is considered less than the corresponding release.
     */
    static int compareVersions(String a, String b) {
        String[] aParts = splitVersion(a);
        String[] bParts = splitVersion(b);
        int max = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < max; i++) {
            String ap = i < aParts.length ? aParts[i] : "0";
            String bp = i < bParts.length ? bParts[i] : "0";
            int cmp;
            if (ap.matches("\\d+") && bp.matches("\\d+")) {
                cmp = Integer.compare(Integer.parseInt(ap), Integer.parseInt(bp));
            } else {
                cmp = ap.compareTo(bp);
            }
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private static String[] splitVersion(String version) {
        if (version == null) {
            return new String[] { "0" };
        }
        // 1.2.3-SNAPSHOT -> [1, 2, 3, SNAPSHOT]
        return version.split("[.\\-]");
    }

    public AccountClient getAccountClient() {
        return new AccountClient(this);
    }

    public AsyncProcessClient getAsyncProcessClient() {
        return new AsyncProcessClient(this);
    }

    public CopyTransClient getCopyTransClient() {
        return new CopyTransClient(this);
    }

    public FileResourceClient getFileResourceClient() {
        return new FileResourceClient(this);
    }

    public GlossaryClient getGlossaryClient() {
        return new GlossaryClient(this);
    }

    public ProjectClient getProjectClient(String projectSlug) {
        return new ProjectClient(this, projectSlug);
    }

    public ProjectIterationClient getProjectIterationClient(String projectSlug,
            String versionSlug) {
        return new ProjectIterationClient(this, projectSlug, versionSlug);
    }

    public ProjectsClient getProjectsClient() {
        return new ProjectsClient(this);
    }

    public SourceDocResourceClient getSourceDocResourceClient(
            String projectSlug, String versionSlug) {
        return new SourceDocResourceClient(this, projectSlug, versionSlug);
    }

    public StatisticsResourceClient getStatisticsClient() {
        return new StatisticsResourceClient(this);
    }

    public TransDocResourceClient getTransDocResourceClient(String projectSlug,
            String versionSlug) {
        return new TransDocResourceClient(this, projectSlug, versionSlug);
    }

    public ProjectIterationLocalesClient getProjectLocalesClient(
            String projectSlug, String versionSlug) {
        return new ProjectIterationLocalesClient(this, projectSlug, versionSlug);
    }
}
