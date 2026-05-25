package org.zanata.spring.i18n;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.HasDynamicTitle;

/**
 * Mixin for any {@link com.vaadin.flow.component.Component} view that wants a
 * locale-aware browser tab title. Implement and return the bundle key from
 * {@link #pageTitleKey()}; this class assembles the full title as
 * {@code "<translated key> | <brand.name>"} and serves it via
 * {@link HasDynamicTitle#getPageTitle()}.
 *
 * <p>Replaces the static {@code @PageTitle("…")} annotation, which can't read
 * the {@link com.vaadin.flow.i18n.I18NProvider} because the annotation value
 * is resolved at class-load time, not per request.</p>
 *
 * <p>The product name comes from the {@code brand.name} bundle key — change
 * the brand in one place and every tab title updates.</p>
 */
public interface TitleKey extends HasDynamicTitle {

    /** Bundle key whose value is the localised tab title (without suffix). */
    String pageTitleKey();

    /** Optional args for {@code MessageFormat}-style {0}/{1} substitution. */
    default Object[] pageTitleArgs() { return new Object[0]; }

    @Override
    default String getPageTitle() {
        UI ui = UI.getCurrent();
        Object[] args = pageTitleArgs();
        if (ui == null) return "!" + pageTitleKey() + "!";
        String base = ui.getTranslation(pageTitleKey(),
                args == null ? new Object[0] : args);
        return base + " | " + ui.getTranslation("brand.name");
    }
}
