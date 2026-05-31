package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/rolerules", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminRoleRulesView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.roleRules"; }


    public record RoleRule(String policyName, String identityPattern, String role) {}

    public AdminRoleRulesView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminRoleRules.heading")));

        Grid<RoleRule> grid = new Grid<>(RoleRule.class, false);
        grid.addColumn(RoleRule::policyName).setHeader(getTranslation("adminRoleRules.colPolicy"));
        grid.addColumn(RoleRule::identityPattern).setHeader(getTranslation("adminRoleRules.colIdentityPattern"));
        grid.addColumn(RoleRule::role).setHeader(getTranslation("adminRoleRules.colRole"));
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
