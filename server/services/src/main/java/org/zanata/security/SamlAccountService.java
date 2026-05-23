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

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.dao.AccountDAO;
import org.zanata.dao.CredentialsDAO;
import org.zanata.model.HAccount;
import org.zanata.model.security.HSaml2Credentials;
import org.zanata.security.annotations.SAML;
import org.zanata.security.annotations.SAMLAttribute;

import java.io.Serializable;
import java.security.Principal;

/**
 * Merge saml credentials to existing account if email matches.
 */
@RequestScoped
public class SamlAccountService implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Logger log =
            LoggerFactory.getLogger(SamlAccountService.class);

    private String email;
    private Principal principal;
    private AccountDAO accountDAO;
    private CredentialsDAO credentialsDAO;

    @SuppressWarnings("unused")
    public SamlAccountService() {
    }

    @Inject
    public SamlAccountService(
            @SAMLAttribute(SAMLAttribute.AttributeName.EMAIL) String email,
            @SAML Principal principal,
            AccountDAO accountDAO,
            CredentialsDAO credentialsDAO) {
        this.email = email;
        this.principal = principal;
        this.accountDAO = accountDAO;
        this.credentialsDAO = credentialsDAO;
    }

    private boolean isFirstSignIn(String uniqueId) {
        return credentialsDAO.findByUser(uniqueId) == null;
    }

    @Transactional
    public void tryMergeToExistingAccount() {
        String uniqueId = principal.getName();
        if (isFirstSignIn(uniqueId)) {
            HAccount hAccount = accountDAO.getByEmail(email);
            if (hAccount != null) {
                log.info(
                        "found existing account with matching email [{}]. Will reuse the account.",
                        email);
                HSaml2Credentials credentials =
                        new HSaml2Credentials(hAccount, uniqueId, email);
                hAccount.getCredentials().add(credentials);
            }
        }
    }
}
