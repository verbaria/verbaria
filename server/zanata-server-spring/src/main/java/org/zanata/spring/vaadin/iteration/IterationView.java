package org.zanata.spring.vaadin.iteration;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.model.HDocument;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/:projectSlug/version/:versionSlug", layout = MainLayout.class)
@PageTitle("Project version | Zanata")
@AnonymousAllowed
public class IterationView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;

    public IterationView(ProjectIterationRepository iterationRepository,
                         DocumentRepository documentRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        String versionSlug = event.getRouteParameters().get("versionSlug").orElse("");

        HProjectIteration iteration = iterationRepository
                .findByProjectAndSlug(projectSlug, versionSlug)
                .orElseThrow(() -> new NotFoundException(
                        "Version not found: " + projectSlug + "/" + versionSlug));

        add(new H2(iteration.getProject().getName() + " - " + iteration.getSlug()));
        add(new Paragraph("Version: " + iteration.getSlug()));

        Grid<HDocument> grid = new Grid<>(HDocument.class, false);
        grid.addColumn(HDocument::getDocId).setHeader("Doc Id").setAutoWidth(true);
        grid.addColumn(HDocument::getPath).setHeader("Path");
        grid.addColumn(d -> d.getContentType() == null ? "" : d.getContentType().toString())
                .setHeader("Content type").setAutoWidth(true);
        grid.addColumn(d -> d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? "" : d.getLocale().getLocaleId().getId())
                .setHeader("Lang").setAutoWidth(true);
        grid.setItems(documentRepository.findByVersion(projectSlug, versionSlug));

        add(grid);
    }
}
