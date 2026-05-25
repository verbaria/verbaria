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
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;

import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.common.EntityStatus;
import org.zanata.common.ProjectType;
import org.zanata.model.HProject;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/create", layout = MainLayout.class)
@PermitAll
public class ProjectCreateView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.newProject"; }


    public ProjectCreateView(ProjectRepository projectRepository,
                             AccountRepository accountRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb(getTranslation("translate.breadcrumb.home"), "/"),
                new Breadcrumb(getTranslation("translate.breadcrumb.projects"), "/explore"),
                new Breadcrumb(getTranslation("projectCreate.title"), "#", true));
        add(crumbs);
        add(new H2(getTranslation("projectCreate.title")));

        TextField slug = new TextField(getTranslation("projectCreate.slug"));
        slug.setRequiredIndicatorVisible(true);
        slug.setHelperText(getTranslation("projectCreate.slugHint"));
        TextField name = new TextField(getTranslation("projectCreate.name"));
        name.setRequiredIndicatorVisible(true);
        TextArea description = new TextArea(getTranslation("projectCreate.description"));
        description.setMaxLength(255);
        ComboBox<ProjectType> defaultType = new ComboBox<>(getTranslation("projectCreate.type"));
        defaultType.setItems(ProjectType.values());
        defaultType.setValue(ProjectType.Gettext);
        ComboBox<EntityStatus> status = new ComboBox<>(getTranslation("projectCreate.status"));
        status.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY);
        status.setValue(EntityStatus.ACTIVE);

        FormLayout form = new FormLayout(slug, name, description, defaultType, status);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        Button create = new Button(getTranslation("projectCreate.submit"), e -> {
            if (slug.getValue() == null || slug.getValue().isBlank()
                    || name.getValue() == null || name.getValue().isBlank()) {
                Notification.show(getTranslation("projectCreate.requiredFields"),
                        3000, Notification.Position.MIDDLE);
                return;
            }
            String s = slug.getValue().trim();
            if (projectRepository.findBySlug(s).isPresent()) {
                Notification.show(getTranslation("projectCreate.duplicate", s),
                        3000, Notification.Position.MIDDLE);
                return;
            }
            HProject p = new HProject();
            p.setSlug(s);
            p.setName(name.getValue().trim());
            p.setDescription(description.getValue());
            p.setDefaultProjectType(defaultType.getValue());
            p.setStatus(status.getValue());
            String username = SecurityContextHolder.getContext().getAuthentication() == null
                    ? null : SecurityContextHolder.getContext().getAuthentication().getName();
            if (username != null) {
                accountRepository.findByUsername(username)
                        .map(a -> a.getPerson())
                        .ifPresent(p::addMaintainer);
            }
            projectRepository.save(p);
            Notification.show(getTranslation("projectCreate.success", s),
                    2500, Notification.Position.BOTTOM_START);
            getUI().ifPresent(ui -> ui.navigate("project/view/" + s));
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"),
                e -> getUI().ifPresent(ui -> ui.navigate("explore")));
        add(new HorizontalLayout(create, cancel));
    }
}
