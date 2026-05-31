package org.verbaria.server.ui.vaadin.i18n;

import java.util.Locale;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinResponse;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinServletRequest;
import jakarta.servlet.http.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps the user's selected UI locale alive across page reloads, browser
 * sessions, and tabs by stashing the choice in a cookie.
 *
 * <p>The cookie survives a server restart and is sent with every subsequent
 * request, so reading it back doesn't require the {@code VaadinSession} to
 * exist yet — we can apply the locale very early in the navigation cycle.</p>
 *
 * <p>Implements {@link VaadinServiceInitListener} so we can register a
 * {@code UIInitListener} that reads the cookie and applies the locale
 * <b>before any view renders</b> — earlier than {@code MainLayout.beforeEnter},
 * which is what the picker, the {@code getTranslation()} calls, and the
 * page's {@code <html lang>} attribute all need.</p>
 *
 * <p>Spring Boot auto-registers {@code VaadinServiceInitListener} beans, so
 * the only thing we need is the {@code @Component} annotation.</p>
 */
@Component
public class LocaleSelector implements VaadinServiceInitListener {

    private static final Logger log = LoggerFactory.getLogger(LocaleSelector.class);

    static final String COOKIE_NAME = "zanata-locale";
    /** ~1 year — locale preference rarely changes. */
    private static final int COOKIE_MAX_AGE_SECONDS = 365 * 24 * 60 * 60;

    public void select(Locale locale) {
        if (locale == null) return;
        writeCookie(locale.toLanguageTag());
        UI ui = UI.getCurrent();
        if (ui != null) ui.setLocale(locale);
    }

    /** Read the locale from the inbound request cookie (no session needed). */
    public Locale current() {
        String tag = readCookie();
        if (tag == null || tag.isBlank()) return null;
        Locale loc = Locale.forLanguageTag(tag);
        return loc == null || "und".equals(loc.toLanguageTag()) ? null : loc;
    }

    /**
     * Apply the persisted locale on every fresh UI — fires before any view
     * renders, so {@code getTranslation()} during view construction sees the
     * right locale and the HTML {@code lang} attribute is correct from the
     * very first paint.
     */
    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiInit -> {
            Locale loc = current();
            if (loc != null) {
                uiInit.getUI().setLocale(loc);
                log.debug("UIInit applied locale from cookie: {}", loc);
            }
        });
    }

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
}
