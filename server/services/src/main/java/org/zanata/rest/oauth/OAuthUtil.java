/*
 * Copyright 2016, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.rest.oauth;

import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.common.base.Strings;

/**
 * OAuth 2.0 request inspection helpers.
 *
 * Originally relied on Apache Oltu which is dead and only ships against
 * javax.servlet. Re-implemented using direct {@link HttpServletRequest}
 * inspection — same OAuth 2.0 header / parameter conventions.
 */
public final class OAuthUtil {

    private static final Logger log = LoggerFactory.getLogger(OAuthUtil.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    // OAuth 2.0 parameter names (formerly from org.apache.oltu.oauth2.common.OAuth)
    private static final String OAUTH_REDIRECT_URI = "redirect_uri";
    private static final String OAUTH_CLIENT_ID    = "client_id";
    private static final String OAUTH_CODE         = "code";
    private static final String OAUTH_REFRESH_TOKEN = "refresh_token";

    private OAuthUtil() {}

    /** Extract a bearer access token from the {@code Authorization} header. */
    public static Optional<String> getAccessTokenFromHeader(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (Strings.isNullOrEmpty(header)) {
            log.debug("no Authorization header");
            return Optional.empty();
        }
        if (header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return Optional.of(header.substring(BEARER_PREFIX.length()).trim());
        }
        return Optional.empty();
    }

    public static Optional<String> getOAuthRedirectURI(HttpServletRequest request) {
        return getNonEmptyString(request.getParameter(OAUTH_REDIRECT_URI));
    }

    public static Optional<String> getOAuthClientId(HttpServletRequest request) {
        return getNonEmptyString(request.getParameter(OAUTH_CLIENT_ID));
    }

    public static Optional<String> getAuthCode(HttpServletRequest request) {
        return getNonEmptyString(request.getParameter(OAUTH_CODE));
    }

    public static Optional<String> getRefreshToken(HttpServletRequest request) {
        return getNonEmptyString(request.getParameter(OAUTH_REFRESH_TOKEN));
    }

    private static Optional<String> getNonEmptyString(String s) {
        if (Strings.isNullOrEmpty(s)) {
            return Optional.empty();
        }
        return Optional.of(s.trim());
    }
}
