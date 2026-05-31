package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.verbaria.server.headless.repository.RoleRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/role/:role", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminRoleDetailView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.role"; }


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
        add(new H2(getTranslation("adminRole.heading", role)));
        roleRepository.findByName(role).ifPresentOrElse(
                r -> add(new Paragraph(getTranslation("adminRole.conditional", r.isConditional()))),
                () -> add(new Paragraph(getTranslation("adminRole.notFound"))));
    }
}
