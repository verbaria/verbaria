package org.verbaria.server.ui.vaadin.i18n;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.LocaleChangeEvent;

/**
 * Static convenience around {@link UI#getTranslation(String, Object...)} so
 * services and non-Component helpers can grab a translation without holding
 * a UI reference. Inside a Vaadin request the current {@link UI} is always
 * available; otherwise the call returns the bracketed key untouched so any
 * misuse is visible.
 *
 * <p>Components should keep using their inherited
 * {@code getTranslation("key", args)} — this helper exists for places like
 * notifications fired from background threads via {@code ui.access(...)}.</p>
 */
public final class I18n {
    private I18n() {}

    public static String t(String key, Object... args) {
        UI ui = UI.getCurrent();
        if (ui == null) return "!" + key + "!";
        return ui.getTranslation(key, args);
    }

    /** Honors the locale on a specific {@link LocaleChangeEvent}. */
    public static String t(LocaleChangeEvent event, String key, Object... args) {
        if (event == null || event.getUI() == null) return "!" + key + "!";
        return event.getUI().getTranslation(key, args);
    }
}
