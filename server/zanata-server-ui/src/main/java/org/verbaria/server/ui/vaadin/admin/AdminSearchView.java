package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.verbaria.server.headless.service.SearchAdminService;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/search", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminSearchView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.reindex"; }


    public AdminSearchView(SearchAdminService searchAdminService) {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminSearch.heading")));
        add(new Paragraph(getTranslation("adminSearch.intro")));

        Button reindex = new Button(getTranslation("adminSearch.reindexAll"));
        reindex.addThemeVariants(ButtonVariant.PRIMARY);
        reindex.addClickListener(e -> {
            reindex.setEnabled(false);
            try {
                searchAdminService.reindexAll();
                Notification.show(getTranslation("adminSearch.complete"),
                        3000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(getTranslation("adminSearch.failed", ex.getMessage()),
                        5000, Notification.Position.MIDDLE);
            } finally {
                reindex.setEnabled(true);
            }
        });
        add(reindex);
    }
}
