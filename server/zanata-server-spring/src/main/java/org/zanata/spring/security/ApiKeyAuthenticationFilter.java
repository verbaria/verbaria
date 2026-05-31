package org.zanata.spring.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.zanata.spring.repository.AccountRepository;

/**
 * Translates the legacy Zanata CLI auth headers — {@code X-Auth-User} and
 * {@code X-Auth-Token} — into a populated {@link SecurityContext}. Matches the
 * contract the {@code zanata-rest-client} module's {@code ApiKeyHeaderFilter}
 * sends on every request. Without this, {@code /rest/**} stays
 * effectively anonymous and the CLI bridge controller's write endpoints
 * reject every push with a 401.
 *
 * <p>The filter is a no-op when the headers are absent or the
 * (username, apiKey) pair does not match a known {@code HAccount}. Form-based
 * login and Vaadin sessions remain unaffected.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_USER = "X-Auth-User";
    public static final String HEADER_TOKEN = "X-Auth-Token";

    private final AccountRepository accountRepository;

    public ApiKeyAuthenticationFilter(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null
                || !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()
                || "anonymousUser".equals(
                        SecurityContextHolder.getContext().getAuthentication().getName())) {
            String user = request.getHeader(HEADER_USER);
            String token = request.getHeader(HEADER_TOKEN);
            if (user != null && !user.isBlank() && token != null && !token.isBlank()) {
                accountRepository.findByUsernameWithRoles(user)
                        .filter(a -> token.equals(a.getApiKey()))
                        .ifPresent(account -> {
                            var authorities = account.getRoles() == null
                                    ? List.<SimpleGrantedAuthority>of()
                                    : account.getRoles().stream()
                                            // Uppercase to match ZanataUserDetailsService
                                            // (form login): HAccountRole.name is stored
                                            // lowercase ("admin"), but Spring's hasRole()
                                            // and Roles.ADMIN_AUTHORITY expect ROLE_ADMIN.
                                            // Without this, API-key admins fail every admin
                                            // check (e.g. push --approve lands as Translated).
                                            .map(r -> new SimpleGrantedAuthority(
                                                    "ROLE_" + r.getName().toUpperCase(java.util.Locale.ROOT)))
                                            .toList();
                            var auth = UsernamePasswordAuthenticationToken
                                    .authenticated(account.getUsername(), null, authorities);
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        });
            }
        }
        chain.doFilter(request, response);
    }
}
