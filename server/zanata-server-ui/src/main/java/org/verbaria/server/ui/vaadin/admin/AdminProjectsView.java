package org.verbaria.server.ui.vaadin.admin;

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.zanata.common.EntityStatus;
import org.zanata.model.HProject;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.ui.vaadin.project.ProjectCreateView;
import org.verbaria.server.ui.vaadin.project.ProjectSettingsView;
import org.verbaria.server.ui.vaadin.project.ProjectView;

@Route(value = "admin/projects", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminProjectsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.myProjects"; }


    public AdminProjectsView(ProjectRepository projectRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(new H2(getTranslation("page.myProjects")));
        header.setWidthFull();
        Button create = new Button(getTranslation("projectCreate.title"),
                e -> getUI().ifPresent(ui -> ui.navigate(ProjectCreateView.class)));
        create.addThemeVariants(ButtonVariant.PRIMARY);
        header.add(create);
        add(header);

        Grid<HProject> grid = new Grid<>(HProject.class, false);
        grid.addComponentColumn(p -> new RouterLink(p.getSlug(), ProjectView.class,
                new com.vaadin.flow.router.RouteParameters("slug", p.getSlug())))
                .setHeader(getTranslation("admin.projects.slugCol")).setAutoWidth(true);
        grid.addColumn(p -> p.getName() == null ? "" : p.getName())
                .setHeader(getTranslation("admin.projects.nameCol")).setAutoWidth(true);
        grid.addColumn(p -> p.getDefaultProjectType() == null
                ? "" : p.getDefaultProjectType().name())
                .setHeader(getTranslation("admin.projects.typeCol")).setAutoWidth(true);
        grid.addComponentColumn(p -> statusEditor(p, projectRepository))
                .setHeader(getTranslation("admin.projects.statusCol")).setAutoWidth(true);
        grid.addComponentColumn(p -> {
            HorizontalLayout actions = new HorizontalLayout();
            Button settings = new Button(getTranslation("page.settings"),
                    e -> getUI().ifPresent(ui ->
                            ui.navigate("project/view/" + p.getSlug() + "/settings")));
            settings.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            Button obsolete = new Button(getTranslation("admin.projects.markObsolete"), e -> {
                p.setStatus(EntityStatus.OBSOLETE);
                projectRepository.save(p);
                Notification.show(getTranslation("admin.projects.markedObsolete", p.getSlug()),
                        2500, Notification.Position.BOTTOM_START);
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            obsolete.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);
            obsolete.setEnabled(p.getStatus() != EntityStatus.OBSOLETE);
            actions.add(settings, obsolete);
            return actions;
        }).setHeader(getTranslation("admin.projects.actionsCol")).setAutoWidth(true);
        // Server-paged DataProvider so admins managing hundreds of projects
        // don't pay a full-table scan to render this view.
        com.vaadin.flow.data.provider.CallbackDataProvider<HProject, Void> dp =
                com.vaadin.flow.data.provider.DataProvider.fromCallbacks(
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
        add(grid);
    }

    private static ComboBox<EntityStatus> statusEditor(HProject p,
                                                       ProjectRepository repo) {
        ComboBox<EntityStatus> cb = new ComboBox<>();
        cb.setItems(EntityStatus.ACTIVE, EntityStatus.READONLY, EntityStatus.OBSOLETE);
        cb.setValue(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
        cb.addValueChangeListener(e -> {
            if (e.getValue() == null) return;
            p.setStatus(e.getValue());
            repo.save(p);
            Notification.show(p.getSlug() + " → " + e.getValue(), 1500,
                    Notification.Position.BOTTOM_START);
        });
        cb.setWidth("130px");
        return cb;
    }
}
