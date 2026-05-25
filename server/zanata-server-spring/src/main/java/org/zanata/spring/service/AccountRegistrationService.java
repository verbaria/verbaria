package org.zanata.spring.service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HPerson;
import org.zanata.spring.repository.AccountRepository;

/**
 * Account creation backing the {@code /account/register} sign-up form.
 *
 * <p>Email-verification flow is not wired yet (no SMTP plumbing in the Spring
 * server). Until it is, accounts are created {@code enabled = true} so the
 * user can log in immediately. The legacy two-step "activate via email" flow
 * is tracked in {@code InactiveAccountView} / resend-activation, which will
 * pivot the behaviour once SMTP lands.</p>
 */
@Service
public class AccountRegistrationService {

    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.-]{3,40}$");

    private static final SecureRandom RNG = new SecureRandom();

    public record Result(HAccount account, boolean enabled) {}

    public static class RegistrationException extends RuntimeException {
        public RegistrationException(String msg) { super(msg); }
    }

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountRegistrationService(AccountRepository accountRepository,
                                      PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Update an existing account's email address. Used by the
     * "update email & resend activation" flow on
     * {@code /account/inactive_account}. Returns true on success, false if
     * the account doesn't exist. Always silently no-ops on an enabled
     * account so we don't leak which usernames exist.
     */
    @Transactional
    public boolean updateEmailForReactivation(String username, String newEmail) {
        if (username == null || username.isBlank()) return false;
        if (newEmail == null || newEmail.isBlank() || !newEmail.contains("@")) return false;
        return accountRepository.findByUsername(username).map(a -> {
            if (a.getPerson() == null) return false;
            a.getPerson().setEmail(newEmail.trim());
            accountRepository.save(a);
            return true;
        }).orElse(false);
    }

    /**
     * Stub for "resend activation email". Until SMTP is wired the service
     * just acknowledges so the UI can give the user feedback; an admin
     * sees the resend attempt in the WARN log.
     */
    public void resendActivation(String username) {
        org.slf4j.LoggerFactory.getLogger(AccountRegistrationService.class)
                .warn("Resend-activation requested for username={} (SMTP not wired)",
                        username == null ? "" : username);
    }

    @Transactional
    public Result register(String username, String password,
                           String fullName, String email) {
        validate(username, password, fullName, email);
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new RegistrationException("That username is already taken.");
        }
        HAccount a = new HAccount();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(password));
        a.setEnabled(true); // see class javadoc — toggle once email lands
        a.setApiKey(generateApiKey());
        a.setRoles(new HashSet<>());

        HPerson p = new HPerson();
        p.setName(fullName == null ? username : fullName.trim());
        p.setEmail(email == null ? "" : email.trim());
        p.setAccount(a);
        a.setPerson(p);

        HAccount saved = accountRepository.save(a);
        return new Result(saved, true);
    }

    private static void validate(String username, String password,
                                 String fullName, String email) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new RegistrationException(
                    "Username must be 3-40 chars, letters/digits/_-./allowed.");
        }
        if (password == null || password.length() < 6) {
            throw new RegistrationException("Password must be at least 6 characters.");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new RegistrationException("A valid email address is required.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new RegistrationException("Full name is required.");
        }
    }

    private static String generateApiKey() {
        byte[] bytes = new byte[16];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
