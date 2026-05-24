package org.zanata.spring.vaadin.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.security.PermitAll;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import org.zanata.common.EntityStatus;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.service.IterationCopyService;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/:slug/add-version", layout = MainLayout.class)
@PageTitle("New version | Zanata")
@PermitAll
public class VersionCreateView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectRepository projectRepository;
    private final IterationCopyService iterationCopyService;

    public VersionCreateView(ProjectRepository projectRepository,
                             IterationCopyService iterationCopyService) {
        this.projectRepository = projectRepository;
        this.iterationCopyService = iterationCopyService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HProject project = projectRepository.findBySlugWithIterations(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));

        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(slug, "/project/view/" + slug),
                new Breadcrumb("New version", "#", true));
        add(crumbs);

        add(new H2("New version for " + (project.getName() == null ? slug : project.getName())));

        TextField versionSlug = new TextField("Version ID");
        versionSlug.setRequiredIndicatorVisible(true);
        versionSlug.setHelperText("e.g. master, 1.0, dev");

        ComboBox<EntityStatus> status = new ComboBox<>("Status");
        status.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY);
        status.setValue(EntityStatus.ACTIVE);

        List<HProjectIteration> existing = new ArrayList<>(project.getProjectIterations());
        existing.sort(Comparator.comparing(HProjectIteration::getSlug,
                String.CASE_INSENSITIVE_ORDER));
        ComboBox<HProjectIteration> copyFrom = new ComboBox<>("Copy from existing version");
        copyFrom.setItems(existing);
        copyFrom.setItemLabelGenerator(HProjectIteration::getSlug);
        copyFrom.setClearButtonVisible(true);
        copyFrom.setHelperText("Optional — pre-populate from another version");

        ComboBox<IterationCopyService.CopyMode> copyMode = new ComboBox<>("Copy what");
        copyMode.setItems(IterationCopyService.CopyMode.NONE,
                IterationCopyService.CopyMode.SOURCE_ONLY,
                IterationCopyService.CopyMode.SOURCE_AND_TRANSLATIONS);
        copyMode.setItemLabelGenerator(m -> switch (m) {
            case NONE -> "Nothing (empty version)";
            case SOURCE_ONLY -> "Source documents only";
            case SOURCE_AND_TRANSLATIONS -> "Source + translations";
        });
        copyMode.setValue(IterationCopyService.CopyMode.NONE);
        copyMode.setEnabled(false);
        copyFrom.addValueChangeListener(e -> {
            copyMode.setEnabled(e.getValue() != null);
            if (e.getValue() != null
                    && copyMode.getValue() == IterationCopyService.CopyMode.NONE) {
                copyMode.setValue(IterationCopyService.CopyMode.SOURCE_ONLY);
            }
        });

        FormLayout form = new FormLayout(versionSlug, status, copyFrom, copyMode);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        Button create = new Button("Create version", e -> {
            if (versionSlug.getValue() == null || versionSlug.getValue().isBlank()) {
                Notification.show("Version ID is required", 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            String src = copyFrom.getValue() == null ? null : copyFrom.getValue().getSlug();
            IterationCopyService.CopyMode mode = copyFrom.getValue() == null
                    ? IterationCopyService.CopyMode.NONE
                    : (copyMode.getValue() == null
                            ? IterationCopyService.CopyMode.SOURCE_ONLY
                            : copyMode.getValue());
            try {
                iterationCopyService.create(slug, versionSlug.getValue().trim(),
                        status.getValue(), src, mode);
                Notification.show("Created version " + versionSlug.getValue(),
                        2500, Notification.Position.BOTTOM_START);
                getUI().ifPresent(ui -> ui.navigate("project/" + slug
                        + "/version/" + versionSlug.getValue().trim()));
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Cancel",
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        add(new HorizontalLayout(create, cancel));
    }
}
