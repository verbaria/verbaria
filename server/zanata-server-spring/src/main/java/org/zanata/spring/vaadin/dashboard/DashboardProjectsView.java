package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.model.HProject;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard/projects", layout = MainLayout.class)
@PageTitle("My Projects | Zanata")
@AnonymousAllowed
public class DashboardProjectsView extends VerticalLayout {

    public DashboardProjectsView(ProjectRepository projectRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("My Projects");

        Grid<HProject> grid = new Grid<>(HProject.class, false);
        grid.addColumn(HProject::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(HProject::getSlug).setHeader("Slug").setAutoWidth(true);
        grid.addColumn(HProject::getDescription).setHeader("Description");
        grid.setItems(projectRepository.findAll());

        grid.addItemClickListener(e -> {
            HProject p = e.getItem();
            if (p != null) {
                getUI().ifPresent(ui -> ui.navigate("project/view/" + p.getSlug()));
            }
        });

        add(heading, grid);
    }
}
