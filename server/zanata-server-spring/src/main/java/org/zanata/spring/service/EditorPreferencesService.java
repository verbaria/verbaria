package org.zanata.spring.service;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountOption;
import org.zanata.spring.repository.AccountRepository;

/**
 * Read/write per-user editor preferences. Persisted as {@link HAccountOption}
 * entries on the user's account so they survive across sessions. Anonymous
 * users get the in-class defaults — no persistence.
 *
 * <p>Keys are namespaced under {@code editor.*} to keep them clearly
 * separate from the AI options under {@code ai.*}.</p>
 */
@Service
public class EditorPreferencesService {

    public static final String KEY_COMPACT_ROWS    = "editor.compactRows";
    public static final String KEY_SHOW_REVIEW     = "editor.showReviewComments";
    public static final String KEY_AUTO_OPEN_HISTORY = "editor.autoOpenHistory";

    public record Prefs(boolean compactRows, boolean showReviewComments,
                        boolean autoOpenHistory) {
        public static final Prefs DEFAULTS = new Prefs(false, true, false);
    }

    private final AccountRepository accountRepository;

    public EditorPreferencesService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /** Current user's preferences, falling back to {@link Prefs#DEFAULTS}. */
    @Transactional(readOnly = true)
    public Prefs load() {
        String user = currentUsername();
        if (user == null) return Prefs.DEFAULTS;
        return accountRepository.findByUsername(user)
                .map(a -> readPrefs(a)).orElse(Prefs.DEFAULTS);
    }

    @Transactional
    public void save(Prefs prefs) {
        String user = currentUsername();
        if (user == null) return; // anonymous — silently ignore
        accountRepository.findByUsername(user).ifPresent(a -> writePrefs(a, prefs));
    }

    private Prefs readPrefs(HAccount account) {
        Map<String, HAccountOption> opts = account.getEditorOptions();
        if (opts == null) return Prefs.DEFAULTS;
        return new Prefs(
                getBool(opts, KEY_COMPACT_ROWS, Prefs.DEFAULTS.compactRows()),
                getBool(opts, KEY_SHOW_REVIEW, Prefs.DEFAULTS.showReviewComments()),
                getBool(opts, KEY_AUTO_OPEN_HISTORY, Prefs.DEFAULTS.autoOpenHistory()));
    }

    private void writePrefs(HAccount account, Prefs p) {
        setBool(account, KEY_COMPACT_ROWS, p.compactRows());
        setBool(account, KEY_SHOW_REVIEW, p.showReviewComments());
        setBool(account, KEY_AUTO_OPEN_HISTORY, p.autoOpenHistory());
        accountRepository.save(account);
    }

    private static boolean getBool(Map<String, HAccountOption> opts,
                                   String key, boolean fallback) {
        HAccountOption o = opts.get(key);
        if (o == null || o.getValue() == null || o.getValue().isBlank()) return fallback;
        return Boolean.parseBoolean(o.getValue().toLowerCase(Locale.ROOT));
    }

    private static void setBool(HAccount a, String key, boolean value) {
        Map<String, HAccountOption> opts = a.getEditorOptions();
        if (opts == null) return;
        HAccountOption o = opts.get(key);
        if (o == null) {
            o = new HAccountOption(key, Boolean.toString(value));
            o.setAccount(a);
            opts.put(key, o);
        } else {
            o.setValue(Boolean.toString(value));
        }
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }
}
