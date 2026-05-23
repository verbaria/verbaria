package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.repository.RoleRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/role/:role", layout = MainLayout.class)
@PageTitle("Role | Zanata")
@RolesAllowed("ADMIN")
public class AdminRoleDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final RoleRepository roleRepository;

    public AdminRoleDetailView(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String role = event.getRouteParameters().get("role").orElse("");
        add(new H2("Role: " + role));
        roleRepository.findByName(role).ifPresentOrElse(
                r -> add(new Paragraph("Conditional: " + r.isConditional())),
                () -> add(new Paragraph("Role not found")));
    }
}
