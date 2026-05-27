package org.zanata.spring.vaadin.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

/**
 * Single item inside a {@link Breadcrumbs} trail.
 *
 * <p>When {@code path} is set, the underlying web component renders the item
 * as a link ({@code <a part="link">}); when {@code path} is {@code null},
 * the item becomes a non-link span ({@code <span part="nolink">}). The
 * parent {@code <vaadin-breadcrumbs>} flags the last item in the trail as
 * the current page (sets {@code aria-current="page"}) when its {@code path}
 * is unset.</p>
 */
@Tag("vaadin-breadcrumbs-item")
@JsModule("./breadcrumbs/vaadin-breadcrumbs-item.js")
public class BreadcrumbsItem extends Component implements HasText, HasStyle {

    /** Builds an item with no label and no path. */
    public BreadcrumbsItem() {
    }

    /** Builds a non-link "current-page" item. */
    public BreadcrumbsItem(String label) {
        setText(label);
    }

    /** Builds an item that links to {@code path} and shows {@code label}. */
    public BreadcrumbsItem(String label, String path) {
        setText(label);
        setPath(path);
    }

    /**
     * Sets the destination URL. Passing {@code null} (or never calling this)
     * turns the item into the non-link "current page" variant.
     */
    public void setPath(String path) {
        if (path == null) {
            getElement().removeProperty("path");
        } else {
            getElement().setProperty("path", path);
        }
    }

    /** Current destination URL, or {@code null} if this is a non-link item. */
    public String getPath() {
        return getElement().getProperty("path");
    }
}
