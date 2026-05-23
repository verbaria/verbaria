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
package org.zanata.servlet;

import java.io.IOException;

import com.google.common.annotations.VisibleForTesting;
import org.zanata.security.AuthenticationManager;
import org.zanata.security.SamlAttributes;
import org.zanata.util.UrlUtil;

import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
@WebFilter(filterName = "ssoFilter")
public class SAMLFilter implements Filter {

    @Inject
    private AuthenticationManager authenticationManager;
    @Inject
    private UrlUtil urlUtil;
    @Inject
    private SamlAttributes samlAttributes;

    public SAMLFilter() {
    }

    @VisibleForTesting
    public SAMLFilter(AuthenticationManager authenticationManager,
            UrlUtil urlUtil, SamlAttributes samlAttributes) {
        this.authenticationManager = authenticationManager;
        this.urlUtil = urlUtil;
        this.samlAttributes = samlAttributes;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            if (samlAttributes.isAuthenticated()) {
                authenticationManager.ssoLogin();
                performRedirection((HttpServletResponse) response);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }

    private void performRedirection(HttpServletResponse resp) throws IOException {
        // Performs the redirection based on the results from the authentication
        // process.
        // This is logic that would normally be in faces-config.xml, but as this is
        // a servlet filter, it cannot take advantage of that.
        String authRedirectResult = authenticationManager.getAuthenticationRedirect();
        switch (authRedirectResult) {
            case "login" -> resp.sendRedirect(urlUtil.signInPage());
            case "edit" -> resp.sendRedirect(urlUtil.createUserPage());
            case "inactive" -> resp.sendRedirect(urlUtil.inactiveAccountPage());
            case "dashboard" -> resp.sendRedirect(urlUtil.dashboardUrl());
            case "redirect" ->
                    // FIXME sso won't preserve continue parameter yet. We just send to dashboard
                    resp.sendRedirect(urlUtil.dashboardUrl());
            case "home" -> resp.sendRedirect(urlUtil.home());
            default -> throw new RuntimeException(
                    "Unexpected authentication manager result: " + authRedirectResult);
        }
    }
}
