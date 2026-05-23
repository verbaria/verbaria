package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/home", layout = MainLayout.class)
@PageTitle("Administration | Zanata")
@RolesAllowed("ADMIN")
public class AdminHomeView extends VerticalLayout {

    public AdminHomeView() {
        setSizeFull();
        setPadding(true);

        add(new H2("Administration"));
        add(new RouterLink("Server settings", AdminServerSettingsView.class));
        add(new RouterLink("User manager", AdminUserManagerView.class));
        add(new RouterLink("Create user", AdminCreateUserView.class));
        add(new RouterLink("Role manager", AdminRoleManagerView.class));
        add(new RouterLink("Role rules", AdminRoleRulesView.class));
        add(new RouterLink("Review criteria", AdminReviewView.class));
        add(new RouterLink("Search / reindex", AdminSearchView.class));
        add(new RouterLink("Cache stats", AdminCacheStatsView.class));
        add(new RouterLink("Stats", AdminStatsView.class));
        add(new RouterLink("Process manager", AdminProcessManagerView.class));
        add(new RouterLink("Monitoring", AdminMonitoringView.class));
    }
}
