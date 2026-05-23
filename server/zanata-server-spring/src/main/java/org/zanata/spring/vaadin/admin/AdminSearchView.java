package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/search", layout = MainLayout.class)
@PageTitle("Reindex | Zanata")
@RolesAllowed("ADMIN")
public class AdminSearchView extends VerticalLayout {

    public AdminSearchView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Search / Reindex"));

        Button reindex = new Button("Reindex");
        reindex.setEnabled(false);
        add(reindex);
    }
}
