package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/home", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminHomeView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.administration"; }


    public AdminHomeView() {
        setSizeFull();
        setPadding(true);

        add(new H2(getTranslation("page.administration")));
        add(new RouterLink(getTranslation("page.homeContent"),     AdminHomeContentView.class));
        add(new RouterLink(getTranslation("page.myProjects"),      AdminProjectsView.class));
        add(new RouterLink(getTranslation("page.groups"),          AdminGroupsView.class));
        add(new RouterLink(getTranslation("page.serverSettings"),  AdminServerSettingsView.class));
        add(new RouterLink(getTranslation("page.aiTranslation"),   AdminAiSettingsView.class));
        add(new RouterLink(getTranslation("page.userManager"),     AdminUserManagerView.class));
        add(new RouterLink(getTranslation("page.createUser"),      AdminCreateUserView.class));
        add(new RouterLink(getTranslation("page.roleManager"),     AdminRoleManagerView.class));
        add(new RouterLink(getTranslation("page.roleRules"),       AdminRoleRulesView.class));
        add(new RouterLink(getTranslation("page.requests"),        AdminRequestsView.class));
        add(new RouterLink(getTranslation("page.reviewCriteria"),  AdminReviewView.class));
        add(new RouterLink(getTranslation("admin.menu.searchReindex"), AdminSearchView.class));
        add(new RouterLink(getTranslation("page.cacheStats"),      AdminCacheStatsView.class));
        add(new RouterLink(getTranslation("admin.menu.stats"),     AdminStatsView.class));
        add(new RouterLink(getTranslation("page.processManager"),  AdminProcessManagerView.class));
        add(new RouterLink(getTranslation("page.monitoring"),      AdminMonitoringView.class));
    }
}
