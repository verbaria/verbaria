package org.zanata.spring.service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HApplicationConfiguration;
import org.zanata.model.HPerson;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ApplicationConfigurationRepository;

/**
 * User-settings operations backing the dashboard "Settings" page:
 * profile/account edits, API key generation, and zanata.ini snippet
 * rendering for CLI/Maven plugin onboarding.
 */
@Service
public class UserSettingsService {

    private static final SecureRandom RNG = new SecureRandom();

    private final AccountRepository accountRepository;
    private final ApplicationConfigurationRepository appConfigRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSettingsService(AccountRepository accountRepository,
                               ApplicationConfigurationRepository appConfigRepository,
                               PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.appConfigRepository = appConfigRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Generate a new 32-char hex API key for the account and persist it. */
    @Transactional
    public String regenerateApiKey(String username) {
        HAccount account = require(username);
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        String key = HexFormat.of().formatHex(bytes);
        account.setApiKey(key);
        accountRepository.save(account);
        return key;
    }

    /**
     * Server URL configured by admin (HApplicationConfiguration "host.url"),
     * or {@code null} if none is set / the stored value isn't a usable URL.
     * Garbage values (random digits, missing scheme, etc.) are rejected so
     * the zanata.ini we render is never poisoned by a stray Admin entry.
     */
    @Transactional(readOnly = true)
    public String serverUrl() {
        return appConfigRepository.findByKey(HApplicationConfiguration.KEY_HOST)
                .map(HApplicationConfiguration::getValue)
                .filter(UserSettingsService::isValidHttpUrl)
                .orElse(null);
    }

    private static boolean isValidHttpUrl(String s) {
        if (s == null || s.isBlank()) return false;
        String lower = s.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        try {
            java.net.URI uri = java.net.URI.create(s);
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Render the zanata.ini snippet matching the legacy
     * {@code dashboard/settings/client} output. {@code requestUrl} is the
     * caller-derived fallback (typically {@code VaadinService} request URL)
     * used when the admin hasn't configured a host.url.
     */
    @Transactional(readOnly = true)
    public String renderZanataIni(String username, String requestUrl) {
        HAccount account = require(username);
        String url = serverUrl();
        if (url == null) {
            url = isValidHttpUrl(requestUrl) ? requestUrl : "http://localhost:8080/";
        }
        if (!url.endsWith("/")) url = url + "/";
        // Match legacy: section name = host with dots replaced by underscores.
        // e.g. https://translate.openstack.org/ -> translate_openstack_org
        String section = sectionNameFor(url);
        String key = account.getApiKey() == null ? "" : account.getApiKey();
        return "[servers]\n"
                + section + ".url=" + url + "\n"
                + section + ".username=" + account.getUsername() + "\n"
                + section + ".key=" + key + "\n";
    }

    /** Convenience overload that defaults to no fallback URL. */
    public String renderZanataIni(String username) {
        return renderZanataIni(username, null);
    }

    private static String sectionNameFor(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            if (host != null && !host.isBlank()) {
                return host.replace('.', '_').replace('-', '_').toLowerCase();
            }
        } catch (IllegalArgumentException ignore) {
            // fall through to default
        }
        return "zanata";
    }

    @Transactional
    public void updateProfile(String username, String displayName, String email) {
        HAccount account = require(username);
        HPerson person = account.getPerson();
        if (person == null) {
            return;
        }
        if (displayName != null) {
            person.setName(displayName.trim());
        }
        if (email != null) {
            person.setEmail(email.trim());
        }
        accountRepository.save(account);
    }

    /** Change the account password. Returns true on success, false if old doesn't match. */
    @Transactional
    public boolean changePassword(String username, String oldPassword, String newPassword) {
        HAccount account = require(username);
        if (account.getPasswordHash() == null
                || !passwordEncoder.matches(oldPassword, account.getPasswordHash())) {
            return false;
        }
        account.setPasswordHash(passwordEncoder.encode(newPassword));
        accountRepository.save(account);
        return true;
    }

    @Transactional(readOnly = true)
    public Optional<HAccount> findAccount(String username) {
        return accountRepository.findByUsername(username);
    }

    private HAccount require(String username) {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("No account: " + username));
    }
}
