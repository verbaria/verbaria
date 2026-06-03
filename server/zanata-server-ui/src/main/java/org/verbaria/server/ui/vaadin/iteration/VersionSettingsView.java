package org.verbaria.server.ui.vaadin.iteration;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.verbaria.server.ui.vaadin.MainLayout;

/**
 * Legacy route — version settings were merged into the project settings UI.
 * Kept as a redirect so old links {@code /project/:slug/version/:v/settings}
 * land on {@code /project/view/:slug/settings}.
 */
@Route(value = "project/:projectSlug/version/:versionSlug/settings", layout = MainLayout.class)
@PermitAll
public class VersionSettingsView extends VerticalLayout
        implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String projectSlug = event.getRouteParameters().get("projectSlug")
                .orElse("");
        event.forwardTo("project/view/" + projectSlug + "/settings");
    }
}
