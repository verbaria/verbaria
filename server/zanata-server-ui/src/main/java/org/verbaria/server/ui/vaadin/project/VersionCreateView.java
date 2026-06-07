package org.verbaria.server.ui.vaadin.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import jakarta.annotation.security.PermitAll;

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
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;

import org.zanata.common.EntityStatus;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.service.IterationCopyService;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "project/:slug/add-version", layout = MainLayout.class)
@PermitAll
public class VersionCreateView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.newVersion"; }


    private final ProjectRepository projectRepository;
    private final IterationCopyService iterationCopyService;
    private final BreadcrumbsService breadcrumbsService;

    public VersionCreateView(ProjectRepository projectRepository,
                             IterationCopyService iterationCopyService,
                             BreadcrumbsService breadcrumbsService) {
        this.projectRepository = projectRepository;
        this.iterationCopyService = iterationCopyService;
        this.breadcrumbsService = breadcrumbsService;
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

        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/projects"),
                BreadcrumbsService.Crumb.of(slug, "/project/view/" + slug),
                BreadcrumbsService.Crumb.here(getTranslation("versionCreate.crumb")));

        add(new H2(getTranslation("versionCreate.headingFor", project.getName() == null ? slug : project.getName())));

        TextField versionSlug = new TextField(getTranslation("versionCreate.versionId"));
        versionSlug.setRequiredIndicatorVisible(true);
        versionSlug.setHelperText(getTranslation("versionCreate.versionIdHint"));

        ComboBox<EntityStatus> status = new ComboBox<>(getTranslation("versionCreate.status"));
        status.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY);
        status.setValue(EntityStatus.ACTIVE);

        List<HProjectIteration> existing = new ArrayList<>(project.getProjectIterations());
        existing.sort(Comparator.comparing(HProjectIteration::getSlug,
                String.CASE_INSENSITIVE_ORDER));
        ComboBox<HProjectIteration> copyFrom = new ComboBox<>(getTranslation("versionCreate.copyFromExisting"));
        copyFrom.setItems(existing);
        copyFrom.setItemLabelGenerator(HProjectIteration::getSlug);
        copyFrom.setClearButtonVisible(true);
        copyFrom.setHelperText(getTranslation("versionCreate.copyFromHint"));

        ComboBox<IterationCopyService.CopyMode> copyMode = new ComboBox<>(getTranslation("versionCreate.copyWhat"));
        copyMode.setItems(IterationCopyService.CopyMode.NONE,
                IterationCopyService.CopyMode.SOURCE_ONLY,
                IterationCopyService.CopyMode.SOURCE_AND_TRANSLATIONS);
        copyMode.setItemLabelGenerator(m -> switch (m) {
            case NONE -> getTranslation("versionCreate.copyMode.none");
            case SOURCE_ONLY -> getTranslation("versionCreate.copyMode.sourceOnly");
            case SOURCE_AND_TRANSLATIONS -> getTranslation("versionCreate.copyMode.sourceAndTranslations");
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

        Button create = new Button(getTranslation("versionCreate.submit"), e -> {
            if (versionSlug.getValue() == null || versionSlug.getValue().isBlank()) {
                Notification.show(getTranslation("versionCreate.versionIdRequired"), 3000,
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
                Notification.show(getTranslation("versionCreate.success", versionSlug.getValue()),
                        2500, Notification.Position.BOTTOM_START);
                getUI().ifPresent(ui -> ui.navigate("project/" + slug
                        + "/version/" + versionSlug.getValue().trim()));
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"),
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        add(new HorizontalLayout(create, cancel));
    }
}
