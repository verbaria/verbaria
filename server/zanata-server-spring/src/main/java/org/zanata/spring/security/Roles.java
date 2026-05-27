package org.zanata.spring.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Roles {

    public static final String ADMIN = "admin";
    public static final String USER = "user";

    public static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    public static final String USER_AUTHORITY = "ROLE_USER";

    public static boolean isAdmin(Authentication auth) {
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || "anonymousUser".equals(auth.getPrincipal())) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> ADMIN_AUTHORITY.equals(a.getAuthority()));
    }

    public static boolean isCurrentUserAdmin() {
        return isAdmin(SecurityContextHolder.getContext().getAuthentication());
    }

    private Roles() {}
}
