package org.zanata.spring.vaadin.admin;

import jakarta.annotation.security.RolesAllowed;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;

import org.springframework.data.domain.PageRequest;
import org.zanata.model.HIterationGroup;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.group.GroupCreateView;
import org.zanata.spring.vaadin.group.GroupView;

@Route(value = "admin/groups", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminGroupsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.groups"; }


    public AdminGroupsView(IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(new H2(getTranslation("page.groups")));
        header.setWidthFull();
        Button create = new Button(getTranslation("groupCreate.title"),
                e -> getUI().ifPresent(ui -> ui.navigate(GroupCreateView.class)));
        create.addThemeVariants(ButtonVariant.PRIMARY);
        header.add(create);
        add(header);

        Grid<HIterationGroup> grid = new Grid<>(HIterationGroup.class, false);
        grid.addComponentColumn(g -> new RouterLink(g.getSlug(), GroupView.class,
                new com.vaadin.flow.router.RouteParameters("slug", g.getSlug())))
                .setHeader(getTranslation("admin.projects.slugCol")).setAutoWidth(true);
        grid.addColumn(g -> g.getName() == null ? "" : g.getName())
                .setHeader(getTranslation("admin.projects.nameCol")).setAutoWidth(true);
        grid.addColumn(g -> g.getDescription() == null ? "" : g.getDescription())
                .setHeader(getTranslation("groupCreate.description")).setAutoWidth(true);
        grid.addColumn(g -> g.getProjectIterations() == null
                ? 0 : g.getProjectIterations().size())
                .setHeader(getTranslation("admin.groups.versionsCol")).setAutoWidth(true);
        grid.addColumn(g -> g.getMaintainers() == null
                ? 0 : g.getMaintainers().size())
                .setHeader(getTranslation("admin.groups.maintainersCol")).setAutoWidth(true);
        grid.addComponentColumn(g -> {
            Button del = new Button(getTranslation("common.delete"), e -> {
                groupRepository.delete(g);
                Notification.show(getTranslation("admin.groups.deletedFmt", g.getSlug()),
                        2500, Notification.Position.BOTTOM_START);
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            del.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);
            return del;
        }).setHeader(getTranslation("admin.projects.actionsCol")).setAutoWidth(true);
        // Server-paged DataProvider so admins with many groups don't pay a
        // full-table scan to render this view.
        com.vaadin.flow.data.provider.CallbackDataProvider<HIterationGroup, Void> dp =
                com.vaadin.flow.data.provider.DataProvider.fromCallbacks(
                        q -> {
                            int page = q.getOffset() / Math.max(1, q.getLimit());
                            return groupRepository.search("",
                                            PageRequest.of(page, q.getLimit()))
                                    .stream();
                        },
                        q -> (int) Math.min(Integer.MAX_VALUE, groupRepository.count()));
        grid.setDataProvider(dp);
        grid.setHeight("70vh");
        add(grid);
    }
}
