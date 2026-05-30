package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.theme.AuraUtility;

@Route(value = "admin/home", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminHomeView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.administration"; }

    public AdminHomeView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2(getTranslation("page.administration")));

        // Vertical stack of titled blocks (the view itself is a VerticalLayout).
        add(block("admin.section.users",
                link("page.userManager", AdminUserManagerView.class),
                link("page.createUser", AdminCreateUserView.class),
                link("page.roleManager", AdminRoleManagerView.class),
                link("page.roleRules", AdminRoleRulesView.class),
                link("page.requests", AdminRequestsView.class)));

        add(block("admin.section.projects",
                link("page.myProjects", AdminProjectsView.class),
                link("page.groups", AdminGroupsView.class),
                link("page.homeContent", AdminHomeContentView.class),
                link("page.reviewCriteria", AdminReviewView.class)));

        add(block("admin.section.server",
                link("page.serverSettings", AdminServerSettingsView.class),
                link("page.aiTranslation", AdminAiSettingsView.class),
                link("admin.menu.searchReindex", AdminSearchView.class),
                link("page.cacheStats", AdminCacheStatsView.class),
                link("admin.menu.stats", AdminStatsView.class),
                link("page.processManager", AdminProcessManagerView.class),
                link("page.monitoring", AdminMonitoringView.class)));
    }

    /** A titled card containing a column of navigation links. */
    private Div block(String titleKey, RouterLink... links) {
        Div card = new Div();
        card.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.Background.BASE, AuraUtility.BoxSizing.BORDER);
        card.getStyle().set("width", "100%");
        card.getStyle().set("max-width", "640px");

        H3 title = new H3(getTranslation(titleKey));
        title.addClassNames(AuraUtility.Margin.NONE,
                AuraUtility.Margin.Bottom.MEDIUM);

        VerticalLayout body = new VerticalLayout(title);
        body.setPadding(false);
        body.setSpacing(false);
        for (RouterLink l : links) {
            body.add(l);
        }
        card.add(body);
        return card;
    }

    private RouterLink link(String key, Class<? extends Component> target) {
        @SuppressWarnings({ "unchecked", "rawtypes" })
        RouterLink link = new RouterLink(getTranslation(key), (Class) target);
        link.addClassNames(AuraUtility.Padding.Vertical.SMALL,
                AuraUtility.TextDecoration.NONE);
        return link;
    }
}
