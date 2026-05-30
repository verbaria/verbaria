package org.zanata.spring.security;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.spring.repository.AccountRepository;

/**
 * Resolves Spring Security users against the HAccount table. The login
 * identifier may be a username or an email — usernames can't contain '@', so
 * the two never collide; we try username first, then email.
 * Roles flow through as ROLE_&lt;name&gt; authorities to match Spring Security's
 * hasRole() convention; the HAccountRole.name column already stores values
 * like "admin", "user" without prefix.
 */
@Service
public class ZanataUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    public ZanataUserDetailsService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        // '@' can't appear in a username, so it unambiguously marks an email.
        HAccount account = (identifier.contains("@")
                ? accountRepository.findFirstByPersonEmailIgnoreCase(identifier)
                : accountRepository.findByUsername(identifier))
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account: " + identifier));

        List<GrantedAuthority> authorities = account.getRoles() == null
                ? List.of()
                : account.getRoles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName().toUpperCase()))
                        .collect(Collectors.toList());

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash() == null ? "" : account.getPasswordHash())
                .authorities(authorities)
                .disabled(!account.isEnabled())
                .build();
    }
}
