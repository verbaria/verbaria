package org.verbaria.server.ui.vaadin.dashboard;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import org.verbaria.server.ui.vaadin.MainLayout;

/**
 * Legacy route — the dashboard was merged into the profile page. Kept as a
 * redirect so old links {@code /dashboard} land on {@code /profile}.
 */
@Route(value = "dashboard", layout = MainLayout.class)
@PermitAll
public class DashboardHomeView extends VerticalLayout
        implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        event.forwardTo("profile");
    }
}
