package org.zanata.spring.vaadin.project;

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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import org.zanata.common.EntityStatus;
import org.zanata.common.ProjectType;
import org.zanata.model.HProject;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/view/:slug/settings", layout = MainLayout.class)
@PageTitle("Project settings | Zanata")
@PermitAll
public class ProjectSettingsView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectRepository projectRepository;

    public ProjectSettingsView(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HProject project = projectRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));

        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(slug, "/project/view/" + slug),
                new Breadcrumb("Settings", "#", true));
        add(crumbs);
        add(new H2(project.getName() == null ? slug : project.getName()));

        TextField name = new TextField("Name");
        name.setValue(project.getName() == null ? "" : project.getName());
        TextArea description = new TextArea("Description");
        description.setMaxLength(255);
        description.setValue(project.getDescription() == null ? "" : project.getDescription());
        ComboBox<ProjectType> defaultType = new ComboBox<>("Default project type");
        defaultType.setItems(ProjectType.values());
        defaultType.setValue(project.getDefaultProjectType() == null
                ? ProjectType.Gettext : project.getDefaultProjectType());
        ComboBox<EntityStatus> status = new ComboBox<>("Status");
        status.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        status.setValue(project.getStatus() == null ? EntityStatus.ACTIVE : project.getStatus());
        TextField sourceView = new TextField("Source view URL");
        sourceView.setValue(project.getSourceViewURL() == null ? "" : project.getSourceViewURL());
        TextField sourceCheckout = new TextField("Source checkout URL");
        sourceCheckout.setValue(project.getSourceCheckoutURL() == null ? "" : project.getSourceCheckoutURL());

        FormLayout form = new FormLayout(name, description, defaultType, status,
                sourceView, sourceCheckout);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        Button save = new Button("Save", e -> {
            project.setName(name.getValue());
            project.setDescription(description.getValue());
            project.setDefaultProjectType(defaultType.getValue());
            project.setStatus(status.getValue());
            project.setSourceViewURL(sourceView.getValue());
            project.setSourceCheckoutURL(sourceCheckout.getValue());
            projectRepository.save(project);
            Notification.show("Saved", 2000, Notification.Position.BOTTOM_START);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button back = new Button("Back to project",
                e -> getUI().ifPresent(ui -> ui.navigate("project/view/" + slug)));
        add(new HorizontalLayout(save, back));
    }
}
