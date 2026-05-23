package org.zanata.security;

import org.zanata.security.annotations.SAML;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.Set;

@ApplicationScoped
public class AuthenticationConfig {

    private final Set<AuthenticationType> authTypes;

    public AuthenticationConfig() {
        this(java.util.Collections.emptySet());
    }

    @Inject
    public AuthenticationConfig(Set<AuthenticationType> authTypes) {
        this.authTypes = authTypes;
    }

    @Produces
    @Named("saml2Enabled")
    @SAML
    public boolean isSaml2Enabled() {
        return authTypes.contains(AuthenticationType.SAML2);
    }
}
