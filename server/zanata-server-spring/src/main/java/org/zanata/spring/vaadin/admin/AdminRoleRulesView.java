package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/rolerules", layout = MainLayout.class)
@PageTitle("Role Rules | Zanata")
@RolesAllowed("ADMIN")
public class AdminRoleRulesView extends VerticalLayout {

    public record RoleRule(String policyName, String identityPattern, String role) {}

    public AdminRoleRulesView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Role Rules"));

        Grid<RoleRule> grid = new Grid<>(RoleRule.class, false);
        grid.addColumn(RoleRule::policyName).setHeader("Policy");
        grid.addColumn(RoleRule::identityPattern).setHeader("Identity Pattern");
        grid.addColumn(RoleRule::role).setHeader("Role");
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
