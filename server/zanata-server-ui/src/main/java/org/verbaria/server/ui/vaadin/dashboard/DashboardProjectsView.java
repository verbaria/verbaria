package org.verbaria.server.ui.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.zanata.model.HProject;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "dashboard/projects", layout = MainLayout.class)
@AnonymousAllowed
public class DashboardProjectsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.myProjects"; }


    public DashboardProjectsView(ProjectRepository projectRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2(getTranslation("dashboardProjects.title"));

        Grid<HProject> grid = new Grid<>(HProject.class, false);
        grid.addColumn(HProject::getName).setHeader(getTranslation("dashboardProjects.colName")).setAutoWidth(true);
        grid.addColumn(HProject::getSlug).setHeader(getTranslation("dashboardProjects.colSlug")).setAutoWidth(true);
        grid.addColumn(HProject::getDescription).setHeader(getTranslation("dashboardProjects.colDescription"));

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
