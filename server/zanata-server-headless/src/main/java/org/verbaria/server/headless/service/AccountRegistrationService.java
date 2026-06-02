package org.verbaria.server.headless.service;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.regex.Pattern;

import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountActivationKey;
import org.zanata.model.HApplicationConfiguration;
import org.zanata.model.HPerson;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.ActivationKeyRepository;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;
import org.verbaria.server.headless.settings.ServerSetting;

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
    private final ApplicationConfigurationRepository configRepository;
    private final ActivationKeyRepository activationKeyRepository;
    private final EmailService emailService;
    private final UserSettingsService userSettingsService;

    public AccountRegistrationService(AccountRepository accountRepository,
                                      PasswordEncoder passwordEncoder,
                                      ApplicationConfigurationRepository configRepository,
                                      ActivationKeyRepository activationKeyRepository,
                                      EmailService emailService,
                                      UserSettingsService userSettingsService) {
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.configRepository = configRepository;
        this.activationKeyRepository = activationKeyRepository;
        this.emailService = emailService;
        this.userSettingsService = userSettingsService;
    }

    /**
     * Whether self-service registration is currently enabled. Defaults to
     * {@code true} when the setting is absent, preserving open registration.
     */
    @Transactional(readOnly = true)
    public boolean isRegistrationAllowed() {
        return configRepository
                .findByKey(ServerSetting.ALLOW_REGISTRATION.key())
                .map(HApplicationConfiguration::getValue)
                .map(v -> !"false".equalsIgnoreCase(v.trim()))
                .orElse(true);
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
        LoggerFactory.getLogger(AccountRegistrationService.class)
                .warn("Resend-activation requested for username={} (SMTP not wired)",
                        username == null ? "" : username);
    }

    @Transactional
    public Result register(String username, String password,
                           String fullName, String email) {
        if (!isRegistrationAllowed()) {
            throw new RegistrationException(
                    "Registration is currently disabled.");
        }
        validate(username, password, fullName, email);
        if (accountRepository.findByUsername(username).isPresent()) {
            throw new RegistrationException("That username is already taken.");
        }
        // When email is configured, require activation; otherwise (e.g. no
        // SMTP in a dev instance) enable immediately so the user can log in.
        boolean requireActivation = emailService.isEnabled();

        HAccount a = new HAccount();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(password));
        a.setEnabled(!requireActivation);
        a.setApiKey(generateApiKey());
        a.setRoles(new HashSet<>());

        HPerson p = new HPerson();
        p.setName(fullName == null ? username : fullName.trim());
        p.setEmail(email == null ? "" : email.trim());
        p.setAccount(a);
        a.setPerson(p);

        HAccount saved = accountRepository.save(a);

        if (requireActivation) {
            sendActivationEmail(saved);
        }
        return new Result(saved, saved.isEnabled());
    }

    /** Creates an activation key and emails the activation link. */
    private void sendActivationEmail(HAccount account) {
        String key = generateApiKey();
        HAccountActivationKey activationKey = new HAccountActivationKey();
        activationKey.setKeyHash(key);
        activationKey.setAccount(account);
        activationKeyRepository.save(activationKey);

        String base = userSettingsService.serverUrl();
        String activationUrl = (base == null ? "" : base)
                + "/account/activate?key=" + key;
        emailService.sendActivation(
                account.getPerson() == null ? null : account.getPerson().getEmail(),
                account.getPerson() == null ? null : account.getPerson().getName(),
                activationUrl);
    }

    /**
     * Activates the account for the given key: enables it and consumes the key.
     * Returns false if the key is unknown (invalid or already used).
     */
    @Transactional
    public boolean activate(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return activationKeyRepository.findById(key).map(activationKey -> {
            HAccount account = activationKey.getAccount();
            if (account != null) {
                account.setEnabled(true);
                accountRepository.save(account);
            }
            activationKeyRepository.delete(activationKey);
            return true;
        }).orElse(false);
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
