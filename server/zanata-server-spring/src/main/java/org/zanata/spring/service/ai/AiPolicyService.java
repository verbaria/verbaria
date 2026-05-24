package org.zanata.spring.service.ai;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountOption;
import org.zanata.spring.repository.AccountRepository;

/**
 * Gatekeeper for the "translate with AI" feature. Combines two switches:
 *
 * <ul>
 *   <li><b>Global</b> {@code ai.translation.enabled} (HApplicationConfiguration) —
 *       admin can disable the feature server-wide. Defaults to {@code false}
 *       so anonymous installs don't get AI buttons until an admin opts in.</li>
 *   <li><b>Per-user</b> {@code ai.translation.allowed} (HAccountOption) —
 *       each logged-in user can opt in/out on their dashboard. Defaults to
 *       {@code true} once the global flag is on.</li>
 * </ul>
 *
 * Anonymous users never see the AI buttons regardless of the global flag.
 */
@Service
public class AiPolicyService {

    public static final String GLOBAL_KEY = "ai.translation.enabled";
    public static final String USER_OPTION_KEY = "ai.translation.allowed";

    private final AiSettingsService appConfig;
    private final AccountRepository accounts;
    private final TranslationProviderRegistry registry;

    public AiPolicyService(AiSettingsService appConfig,
                           AccountRepository accounts,
                           TranslationProviderRegistry registry) {
        this.appConfig = appConfig;
        this.accounts = accounts;
        this.registry = registry;
    }

    /** Server-wide AI feature toggle (admin → AI translation page). */
    public boolean isGloballyEnabled() {
        String v = appConfig.getOrNull(GLOBAL_KEY);
        return v != null && Boolean.parseBoolean(v);
    }

    public void setGloballyEnabled(boolean enabled) {
        appConfig.set(GLOBAL_KEY, Boolean.toString(enabled));
    }

    /** Per-user toggle from the dashboard Profile tab. Default {@code true}. */
    @Transactional(readOnly = true)
    public boolean isAllowedForUser(String username) {
        if (username == null) return false;
        return accounts.findByUsername(username)
                .map(this::readUserAllowed)
                .orElse(false);
    }

    @Transactional
    public void setAllowedForUser(String username, boolean allowed) {
        HAccount account = accounts.findByUsername(username).orElse(null);
        if (account == null) return;
        var opts = account.getEditorOptions();
        if (opts == null) return;
        HAccountOption opt = opts.get(USER_OPTION_KEY);
        if (opt == null) {
            opt = new HAccountOption(USER_OPTION_KEY, Boolean.toString(allowed));
            opt.setAccount(account);
            opts.put(USER_OPTION_KEY, opt);
        } else {
            opt.setValue(Boolean.toString(allowed));
        }
        accounts.save(account);
    }

    private boolean readUserAllowed(HAccount account) {
        var opts = account.getEditorOptions();
        if (opts == null) return true; // opt-out, not opt-in
        HAccountOption opt = opts.get(USER_OPTION_KEY);
        if (opt == null || opt.getValue() == null || opt.getValue().isBlank()) return true;
        return Boolean.parseBoolean(opt.getValue());
    }

    /**
     * Full gate for showing AI translate UI: global flag on, at least one
     * provider configured, user authenticated and not opted out.
     *
     * <p>Must be {@code @Transactional} so the lazy {@code editorOptions}
     * map on {@link HAccount} stays attached to a session for the
     * {@code readUserAllowed} check. (Calling {@code isAllowedForUser}
     * through {@code this::} bypasses the CGLIB proxy, so its own
     * annotation would have no effect.)
     */
    @Transactional(readOnly = true)
    public boolean canCurrentUserUseAi() {
        if (!isGloballyEnabled()) return false;
        if (registry.available().isEmpty()) return false;
        Optional<String> username = currentUsername();
        return username.map(this::isAllowedForUser).orElse(false);
    }

    private static Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null) {
            return Optional.empty();
        }
        return Optional.of(auth.getName());
    }
}
