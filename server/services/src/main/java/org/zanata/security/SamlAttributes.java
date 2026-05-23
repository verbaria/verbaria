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

import io.undertow.security.idm.Account;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpSession;

import org.zanata.cdi.DeltaSpike;
import org.picketlink.common.constants.GeneralConstants;
import org.picketlink.identity.federation.bindings.wildfly.sp.SPFormAuthenticationMechanism;
import org.zanata.security.annotations.SAML;
import org.zanata.security.annotations.SAMLAttribute;
import org.zanata.security.annotations.SAMLAttribute.AttributeName;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Patrick Huang <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class SamlAttributes {

    private HttpSession session;
    private Map<String, List<String>> attributeMap;

    @SuppressWarnings("unused")
    public SamlAttributes() {
    }

    @Inject
    public SamlAttributes(@DeltaSpike HttpSession session) {
        this.session = session;
        @SuppressWarnings("unchecked")
        Map<String, List<String>> map = (Map<String, List<String>>) session
                .getAttribute(GeneralConstants.SESSION_ATTRIBUTE_MAP);
        this.attributeMap = map != null ? map : Collections.emptyMap();
    }

    public boolean isAuthenticated() {
        return principalFromSAMLResponse() != null;
    }

    @Produces
    @SAML
    public Principal principalFromSAMLResponse() {
        Object obj = session.getAttribute(
                SPFormAuthenticationMechanism.FORM_ACCOUNT_NOTE);
        if (obj instanceof Account account
                && account.getRoles().contains("authenticated")) {
            return account.getPrincipal();
        }
        return null;
    }

    @Produces
    @SAMLAttribute(AttributeName.UID)
    public String usernameFromSAMLResponse(@SAML Principal principal) {
        if (principal == null) return null;
        String principalName = principal.getName();
        // In some IDPs, this may be a more readable username than principal name.
        return SamlAttributesUtil.getValueFromSessionAttribute(
                attributeMap, AttributeName.UID.getKey(), principalName);
    }

    @Produces
    @SAMLAttribute(AttributeName.CN)
    public String commonNameFromSAMLResponse(@SAML Principal principal) {
        if (principal == null) return null;
        return SamlAttributesUtil.getValueFromSessionAttribute(
                attributeMap, AttributeName.CN.getKey());
    }

    @Produces
    @SAMLAttribute(AttributeName.EMAIL)
    public String emailFromSAMLResponse(@SAML Principal principal) {
        if (principal == null) return null;
        return SamlAttributesUtil.getValueFromSessionAttribute(
                attributeMap, AttributeName.EMAIL.getKey());
    }
}
