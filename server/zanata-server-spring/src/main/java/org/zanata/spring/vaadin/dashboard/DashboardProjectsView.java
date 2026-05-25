package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

        // Server-paged DataProvider — never loads the full project table.
        CallbackDataProvider<HProject, Void> dp = DataProvider.fromCallbacks(
                q -> {
                    int page = q.getOffset() / Math.max(1, q.getLimit());
                    return projectRepository.findAll(
                                    PageRequest.of(page, q.getLimit(),
                                            Sort.by("slug")))
                            .stream();
                },
                q -> (int) Math.min(Integer.MAX_VALUE, projectRepository.count()));
        grid.setDataProvider(dp);
        grid.setHeight("70vh");

        grid.addItemClickListener(e -> {
            HProject p = e.getItem();
            if (p != null) {
                getUI().ifPresent(ui -> ui.navigate("project/view/" + p.getSlug()));
            }
        });

        add(heading, grid);
    }
}
