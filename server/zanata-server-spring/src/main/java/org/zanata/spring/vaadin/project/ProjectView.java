package org.zanata.spring.vaadin.project;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/view/:slug", layout = MainLayout.class)
@PageTitle("Project | Zanata")
@AnonymousAllowed
public class ProjectView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectRepository projectRepository;

    public ProjectView(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HProject project = projectRepository.findBySlugWithIterations(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));

        add(new H2(project.getName() == null ? slug : project.getName()));
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            add(new Paragraph(project.getDescription()));
        }
        if (project.getSourceViewURL() != null && !project.getSourceViewURL().isBlank()) {
            add(new Anchor(project.getSourceViewURL(), "Source view URL"));
        }
        if (project.getSourceCheckoutURL() != null && !project.getSourceCheckoutURL().isBlank()) {
            add(new Anchor(project.getSourceCheckoutURL(), "Source checkout URL"));
        }

        add(new H3("Versions"));
        Grid<HProjectIteration> grid = new Grid<>(HProjectIteration.class, false);
        grid.addColumn(HProjectIteration::getSlug).setHeader("Version").setAutoWidth(true);
        grid.addColumn(i -> i.getStatus() == null ? "" : i.getStatus().name())
                .setHeader("Status").setAutoWidth(true);
        grid.setItems(List.copyOf(project.getProjectIterations()));
        grid.addItemClickListener(e -> {
            HProjectIteration i = e.getItem();
            if (i != null) {
                getUI().ifPresent(ui -> ui.navigate(
                        "project/" + slug + "/version/" + i.getSlug()));
            }
        });
        add(grid);

        add(new H3("Glossary"),
                new Anchor("/glossary/project/" + slug, "Project glossary"));

        if (project.getHomeContent() != null && !project.getHomeContent().isBlank()) {
            add(new H3("About"), new Paragraph(project.getHomeContent()));
        }
    }
}
