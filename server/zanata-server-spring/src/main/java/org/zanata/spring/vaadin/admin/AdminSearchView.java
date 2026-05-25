package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.service.SearchAdminService;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/search", layout = MainLayout.class)
@PageTitle("Reindex | Zanata")
@RolesAllowed("ADMIN")
public class AdminSearchView extends VerticalLayout {

    public AdminSearchView(SearchAdminService searchAdminService) {
        setSizeFull();
        setPadding(true);
        add(new H2("Search / Reindex"));
        add(new Paragraph("Rebuild the Hibernate Search indices."));

        Button reindex = new Button("Reindex all");
        reindex.addThemeVariants(ButtonVariant.PRIMARY);
        reindex.addClickListener(e -> {
            reindex.setEnabled(false);
            try {
                searchAdminService.reindexAll();
                Notification.show("Reindex complete",
                        3000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show("Reindex failed: " + ex.getMessage(),
                        5000, Notification.Position.MIDDLE);
            } finally {
                reindex.setEnabled(true);
            }
        });
        add(reindex);
    }
}
