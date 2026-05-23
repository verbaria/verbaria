package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/stats", layout = MainLayout.class)
@PageTitle("Server Stats | Zanata")
@RolesAllowed("ADMIN")
public class AdminStatsView extends VerticalLayout {

    public AdminStatsView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Server Stats"));
        add(new Paragraph("No data"));
    }
}
