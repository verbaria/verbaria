package org.verbaria.server.ui.vaadin.theme;

import java.util.Locale;

import com.vaadin.flow.component.html.Anchor;
import org.vaadin.lineawesome.LineAwesomeIcon;

/** Builds the "view source" link for a project, with a GitHub icon when apt. */
public final class SourceLinks {

    private SourceLinks() {
    }

    /**
     * An icon-only link to {@code url} (opens in a new tab), or {@code null}
     * when the url is blank. A GitHub URL gets the GitHub mark; anything else
     * an external-link glyph. Clicks don't bubble, so embedding it in a
     * clickable grid row won't also trigger the row's navigation.
     */
    public static Anchor of(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        boolean github = url.toLowerCase(Locale.ROOT).contains("github.com");
        Anchor link = new Anchor(url, (github
                ? LineAwesomeIcon.GITHUB
                : LineAwesomeIcon.EXTERNAL_LINK_ALT_SOLID).create());
        link.setTarget("_blank");
        link.getElement().setAttribute("title", url);
        link.getElement().setAttribute("aria-label",
                github ? "GitHub source" : "Source");
        link.addClassNames(AuraUtility.TextColor.SECONDARY);
        // Centre the SVG instead of letting it sit on the text baseline (which
        // looks off next to a large heading).
        link.getStyle().set("display", "inline-flex");
        link.getStyle().set("align-items", "center");
        link.getElement().addEventListener("click", e -> {
        }).stopPropagation();
        return link;
    }
}
