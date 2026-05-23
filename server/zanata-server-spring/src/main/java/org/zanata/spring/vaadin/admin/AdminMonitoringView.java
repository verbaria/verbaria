package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/monitoring", layout = MainLayout.class)
@PageTitle("Monitoring | Zanata")
@RolesAllowed("ADMIN")
public class AdminMonitoringView extends VerticalLayout {

    public AdminMonitoringView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Monitoring"));
        add(new Anchor("/actuator/health", "Health"));
        add(new Anchor("/actuator/metrics", "Metrics"));
    }
}
