package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/processmanager", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminProcessManagerView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.processManager"; }


    public record ProcessRow(String id, String description, String status, String progress) {}

    public AdminProcessManagerView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminProcess.heading")));

        Grid<ProcessRow> grid = new Grid<>(ProcessRow.class, false);
        grid.addColumn(ProcessRow::id).setHeader(getTranslation("adminProcess.colId"));
        grid.addColumn(ProcessRow::description).setHeader(getTranslation("adminProcess.colDescription"));
        grid.addColumn(ProcessRow::status).setHeader(getTranslation("adminProcess.colStatus"));
        grid.addColumn(ProcessRow::progress).setHeader(getTranslation("adminProcess.colProgress"));
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
