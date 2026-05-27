package org.zanata.spring.vaadin.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

/**
 * Temporary Flow wrapper for the {@code <vaadin-breadcrumbs>} web component
 * shipped by Vaadin in {@code @vaadin/breadcrumbs} (25.2 alpha line).
 *
 * <p>Vaadin Flow has not (yet) published an official Java wrapper for this
 * component. We host a minimal one here so we can drop the legacy
 * {@code com.vaadin.componentfactory:breadcrumb} add-on, which doesn't
 * track the new built-in component.</p>
 *
 * <p>The web component is gated behind a Vaadin experimental feature flag.
 * Enable it through {@code vaadin-featureflags.properties}:
 * <pre>com.vaadin.experimental.breadcrumbsComponent=true</pre>
 *
 * <p>Once Vaadin ships a stable Java API, delete this class and the
 * companion {@link BreadcrumbsItem}; the package name in the addon's API
 * was {@code com.vaadin.componentfactory.Breadcrumbs} — pick whichever the
 * official one ends up using and re-point imports.</p>
 *
 * <p>Usage:
 * <pre>
 *   Breadcrumbs trail = new Breadcrumbs();
 *   trail.add(new BreadcrumbsItem("Home", "/"));
 *   trail.add(new BreadcrumbsItem("Projects", "/projects"));
 *   trail.add(new BreadcrumbsItem("My project")); // no path → current page
 * </pre>
 */
@Tag("vaadin-breadcrumbs")
// Vendored Vaadin breadcrumbs source from
// https://github.com/vaadin/web-components/tree/main/packages/breadcrumbs
// (post-alpha11). The published `@vaadin/breadcrumbs@25.2.0-alpha11` npm
// package is a placeholder whose `render()` returns empty html (no slot,
// no list, no overflow), so light-DOM items never render. The main-branch
// source has the full template — vendoring it sidesteps the broken alpha
// and avoids a temporary shim. Drop these files when an npm release with
// a working `render()` lands.
@JsModule("./breadcrumbs/vaadin-breadcrumbs.js")
public class Breadcrumbs extends Component implements HasComponents, HasSize, HasStyle {

    public Breadcrumbs() {
    }

    public Breadcrumbs(BreadcrumbsItem... items) {
        add(items);
    }
}
