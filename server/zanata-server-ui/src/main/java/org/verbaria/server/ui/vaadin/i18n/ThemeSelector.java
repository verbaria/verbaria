package org.verbaria.server.ui.vaadin.i18n;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.page.ColorScheme;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.Cookie;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountOption;
import org.verbaria.server.headless.repository.AccountRepository;

/**
 * Keeps the user's colour-scheme preference alive across reloads, sessions
 * and devices.
 *
 * <ul>
 *   <li><b>Signed-in users</b> → persisted as an {@link HAccountOption} on the
 *       account ({@code ui.theme}). {@code null} / missing = "not set".</li>
 *   <li><b>Anonymous users</b> → cookie, same pattern as
 *       {@link LocaleSelector}. Lets the choice survive sign-up so the first
 *       authenticated render matches what the user picked while browsing.</li>
 * </ul>
 *
 * <p>Theme application uses Vaadin 25's native
 * {@link com.vaadin.flow.component.page.Page#setColorScheme(ColorScheme.Value)
 * Page.setColorScheme} — no inline JavaScript, no document-level attribute
 * juggling. Vaadin handles the {@code prefers-color-scheme} media query and
 * {@code <html>} attribute update itself.</p>
 */
@Component
public class ThemeSelector implements VaadinServiceInitListener {

    /** Persisted preference. SYSTEM = follow the OS / browser. */
    public enum Mode {
        LIGHT(ColorScheme.Value.LIGHT),
        DARK(ColorScheme.Value.DARK),
        SYSTEM(ColorScheme.Value.SYSTEM);

        final ColorScheme.Value vaadinValue;
        Mode(ColorScheme.Value v) { this.vaadinValue = v; }
    }

    static final String COOKIE_NAME = "zanata-theme";
    /** Key under {@code HAccount.editorOptions} for the persisted theme. */
    public static final String OPTION_KEY = "ui.theme";
    /** ~1 year — theme preference rarely changes. */
    private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    private final AccountRepository accountRepository;
    private final TransactionTemplate readTx;
    private final TransactionTemplate writeTx;

    public ThemeSelector(AccountRepository accountRepository,
                         PlatformTransactionManager txManager) {
        this.accountRepository = accountRepository;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.writeTx = new TransactionTemplate(txManager);
    }

    /**
     * Save and apply the preference. Authenticated users → profile, anonymous
     * users → cookie. Either way, the current UI is updated immediately.
     *
     * <p>We use {@link TransactionTemplate} rather than {@code @Transactional}
     * because this method is invoked from a Vaadin click listener (and the
     * service-init UI listener for {@link #currentExplicit()}) — both paths
     * bypass the Spring proxy that would otherwise open the transaction. The
     * template guarantees an active session while we touch the lazy
     * {@code editorOptions} collection.</p>
     */
    public void select(Mode mode) {
        if (mode == null) return;
        String user = currentUsername();
        if (user != null) {
            writeTx.executeWithoutResult(status ->
                    accountRepository.findByUsername(user).ifPresent(a -> {
                        writeOption(a, mode.name().toLowerCase(Locale.ROOT));
                        accountRepository.save(a);
                    }));
        } else {
            writeCookie(mode.name().toLowerCase(Locale.ROOT));
        }
        apply(UI.getCurrent(), mode);
    }

    /**
     * Explicitly-chosen preference. Empty when neither the profile nor the
     * cookie has a value — {@code null = not set}, as the user requested.
     */
    public Optional<Mode> currentExplicit() {
        String user = currentUsername();
        if (user != null) {
            // Read the option value inside an active transaction — touching
            // the lazy {@code editorOptions} map outside one throws
            // LazyInitializationException.
            String raw = readTx.execute(status ->
                    accountRepository.findByUsername(user)
                            .map(ThemeSelector::readOption).orElse(null));
            if (raw != null && !raw.isBlank()) {
                Optional<Mode> parsed = parse(raw);
                if (parsed.isPresent()) return parsed;
            }
        }
        String c = readCookie();
        return c == null || c.isBlank() ? Optional.empty() : parse(c);
    }

    /** Mode for the UI to display. Falls back to {@link Mode#SYSTEM}. */
    public Mode current() {
        return currentExplicit().orElse(Mode.SYSTEM);
    }

    /**
     * Apply the persisted theme on every fresh UI — fires before any view
     * renders, so the chrome paints in the right colour scheme from the
     * very first frame. When nothing's stored we leave Vaadin alone, which
     * means {@link ColorScheme.Value#NORMAL} (no opinion).
     */
    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit ->
                currentExplicit().ifPresent(m -> apply(uiInit.getUI(), m)));
    }

    private static void apply(UI ui, Mode mode) {
        if (ui == null || mode == null) return;
        ui.getPage().setColorScheme(mode.vaadinValue);
    }

    private static Optional<Mode> parse(String s) {
        if (s == null) return Optional.empty();
        try {
            return Optional.of(Mode.valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    // ---- HAccountOption ----

    private static String readOption(HAccount account) {
        Map<String, HAccountOption> opts = account.getEditorOptions();
        if (opts == null) return null;
        HAccountOption o = opts.get(OPTION_KEY);
        return o == null ? null : o.getValue();
    }

    private static void writeOption(HAccount account, String value) {
        Map<String, HAccountOption> opts = account.getEditorOptions();
        if (opts == null) return;
        HAccountOption o = opts.get(OPTION_KEY);
        if (o == null) {
            o = new HAccountOption(OPTION_KEY, value);
            o.setAccount(account);
            opts.put(OPTION_KEY, o);
        } else {
            o.setValue(value);
        }
    }

    // ---- Cookie (anonymous fallback) ----

    private static void writeCookie(String value) {
        VaadinResponse resp = VaadinService.getCurrentResponse();
        if (resp == null) return;
        Cookie c = new Cookie(COOKIE_NAME, value);
        c.setPath("/");
        c.setMaxAge(COOKIE_MAX_AGE_SECONDS);
        c.setHttpOnly(false);
        resp.addCookie(c);
    }

    private static String readCookie() {
        VaadinRequest req = VaadinService.getCurrentRequest();
        if (!(req instanceof VaadinServletRequest sr)) return null;
        Cookie[] cookies = sr.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }
}
