package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.model.HAccountRole;
import org.zanata.spring.repository.RoleRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/rolemanager", layout = MainLayout.class)
@PageTitle("Role Manager | Zanata")
@RolesAllowed("ADMIN")
public class AdminRoleManagerView extends VerticalLayout {

    public AdminRoleManagerView(RoleRepository roleRepository) {
        setSizeFull();
        setPadding(true);
        add(new H2("Role Manager"));

        Grid<HAccountRole> grid = new Grid<>(HAccountRole.class, false);
        grid.addColumn(HAccountRole::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(HAccountRole::isConditional).setHeader("Conditional").setAutoWidth(true);
        grid.setItems(roleRepository.findAll());

        grid.addItemClickListener(e -> {
            HAccountRole r = e.getItem();
            if (r != null) {
                getUI().ifPresent(ui -> ui.navigate("admin/role/" + r.getName()));
            }
        });

        add(grid);
    }
}
