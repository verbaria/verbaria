/*
 * Copyright 2010, Red Hat, Inc. and individual contributors
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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.dao.CredentialsDAO;
import org.zanata.events.AlreadyLoggedInEvent;
import org.zanata.exception.NotLoggedInException;
import org.zanata.model.security.HCredentials;
import org.zanata.security.annotations.SAML;
import org.zanata.security.annotations.SAMLAttribute;
import org.zanata.security.annotations.SAMLAttribute.AttributeName;
import org.zanata.util.Synchronized;

import java.io.Serializable;
import java.security.Principal;

@SessionScoped
@Synchronized
public class SamlIdentity
        implements Serializable, ExternallyAuthenticatedIdentity {

    private static final long serialVersionUID = 5341594999046279309L;
    private static final Logger log =
            LoggerFactory.getLogger(SamlIdentity.class);

    private ZanataIdentity identity;
    @SuppressFBWarnings("SE_BAD_FIELD")
    private Event<AlreadyLoggedInEvent> alreadyLoggedInEvent;
    private Principal principal;
    private String uid;
    private String commonName;
    private String email;
    private CredentialsDAO credentialsDAO;

    @SuppressWarnings("unused")
    public SamlIdentity() {
    }

    @Inject
    public SamlIdentity(ZanataIdentity identity,
            Event<AlreadyLoggedInEvent> alreadyLoggedInEvent,
            @SAML Principal principal,
            @SAMLAttribute(AttributeName.UID) String uid,
            @SAMLAttribute(AttributeName.CN) String commonName,
            @SAMLAttribute(AttributeName.EMAIL) String email,
            CredentialsDAO credentialsDAO) {
        this.identity = identity;
        this.alreadyLoggedInEvent = alreadyLoggedInEvent;
        this.principal = principal;
        this.uid = uid;
        this.commonName = commonName;
        this.email = email;
        this.credentialsDAO = credentialsDAO;
    }

    public String getUid() {
        return uid;
    }

    public String getCommonName() {
        return commonName;
    }

    public String getEmail() {
        return email;
    }

    public String getUniqueName() {
        return principal != null ? principal.getName() : null;
    }

    @Override
    public void authenticate() {
        if (identity.isLoggedIn()) {
            alreadyLoggedInEvent.fire(new AlreadyLoggedInEvent());
            return;
        }
        if (principal == null) throw new NotLoggedInException();

        log.info("SAML2 login: username: {}, common name: {}, uuid: {}",
                AttributeName.UID, commonName, getUniqueName());
        HCredentials credentials = credentialsDAO.findByUser(getUniqueName());
        // when sign in with SAML2 the first time, there is no HCredentials or HAccount in database
        String username = (credentials != null && credentials.getAccount() != null)
                ? credentials.getAccount().getUsername()
                : uid;

        identity.getCredentials().setUsername(username);
        identity.getCredentials().setPassword("");
        identity.getCredentials().setAuthType(AuthenticationType.SAML2);
        identity.getCredentials().setInitialized(true);
        identity.setPreAuthenticated(true);
    }

    @Override
    public void login() {
        if (identity.isLoggedIn()) {
            alreadyLoggedInEvent.fire(new AlreadyLoggedInEvent());
            return;
        }

        if (principal == null) throw new NotLoggedInException();
        identity.acceptExternallyAuthenticatedPrincipal(principal);
    }
}
