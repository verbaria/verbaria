package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.model.HAccountRole;
import org.verbaria.server.headless.repository.RoleRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/rolemanager", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminRoleManagerView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.roleManager"; }


    public AdminRoleManagerView(RoleRepository roleRepository) {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminRoleManager.heading")));

        Grid<HAccountRole> grid = new Grid<>(HAccountRole.class, false);
        grid.addColumn(HAccountRole::getName).setHeader(getTranslation("adminRoleManager.colName")).setAutoWidth(true);
        grid.addColumn(HAccountRole::isConditional).setHeader(getTranslation("adminRoleManager.colConditional")).setAutoWidth(true);
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
