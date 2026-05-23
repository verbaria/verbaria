package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/processmanager", layout = MainLayout.class)
@PageTitle("Process Manager | Zanata")
@RolesAllowed("ADMIN")
public class AdminProcessManagerView extends VerticalLayout {

    public record ProcessRow(String id, String description, String status, String progress) {}

    public AdminProcessManagerView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Background Processes"));

        Grid<ProcessRow> grid = new Grid<>(ProcessRow.class, false);
        grid.addColumn(ProcessRow::id).setHeader("Id");
        grid.addColumn(ProcessRow::description).setHeader("Description");
        grid.addColumn(ProcessRow::status).setHeader("Status");
        grid.addColumn(ProcessRow::progress).setHeader("Progress");
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
