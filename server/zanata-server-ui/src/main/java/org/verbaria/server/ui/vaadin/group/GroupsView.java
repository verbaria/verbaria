package org.verbaria.server.ui.vaadin.group;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.model.HIterationGroup;
import org.verbaria.server.headless.repository.IterationGroupRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "groups", layout = MainLayout.class)
@AnonymousAllowed
public class GroupsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.groups"; }


    public GroupsView(IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(new H2(getTranslation("groups.title")));
        header.setWidthFull();
        if (isAuthenticated()) {
            Button create = new Button(getTranslation("groups.newGroup"),
                    e -> getUI().ifPresent(ui -> ui.navigate("group/create")));
            create.addThemeVariants(ButtonVariant.PRIMARY);
            header.add(create);
        }
        add(header);

        Grid<HIterationGroup> grid = new Grid<>(HIterationGroup.class, false);
        grid.addComponentColumn(g -> new RouterLink(g.getSlug(), GroupView.class,
                new com.vaadin.flow.router.RouteParameters("slug", g.getSlug())))
                .setHeader(getTranslation("groups.colSlug")).setAutoWidth(true);
        grid.addColumn(g -> g.getName() == null ? "" : g.getName())
                .setHeader(getTranslation("groups.colName")).setAutoWidth(true);
        grid.addColumn(g -> g.getDescription() == null ? "" : g.getDescription())
                .setHeader(getTranslation("groups.colDescription")).setAutoWidth(true);
        grid.setItems(groupRepository.search("", PageRequest.of(0, 100)).getContent());
        grid.setAllRowsVisible(true);
        add(grid);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
