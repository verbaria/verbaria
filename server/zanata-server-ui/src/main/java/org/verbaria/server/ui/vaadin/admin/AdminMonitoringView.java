package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/monitoring", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminMonitoringView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.monitoring"; }


    public AdminMonitoringView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminMonitoring.heading")));
        Anchor health = new Anchor("/actuator/health", getTranslation("adminMonitoring.health"));
        health.setTarget("_blank");
        Anchor metrics = new Anchor("/actuator/metrics", getTranslation("adminMonitoring.metrics"));
        metrics.setTarget("_blank");
        add(health, metrics);
    }
}
