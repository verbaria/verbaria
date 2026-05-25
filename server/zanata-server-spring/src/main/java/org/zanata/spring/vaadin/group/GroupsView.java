package org.zanata.spring.vaadin.group;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.model.HIterationGroup;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "groups", layout = MainLayout.class)
@PageTitle("Groups | Zanata")
@AnonymousAllowed
public class GroupsView extends VerticalLayout {

    public GroupsView(IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        HorizontalLayout header = new HorizontalLayout(new H2("Version groups"));
        header.setWidthFull();
        if (isAuthenticated()) {
            Button create = new Button("New group",
                    e -> getUI().ifPresent(ui -> ui.navigate("group/create")));
            create.addThemeVariants(ButtonVariant.PRIMARY);
            header.add(create);
        }
        add(header);

        Grid<HIterationGroup> grid = new Grid<>(HIterationGroup.class, false);
        grid.addComponentColumn(g -> new RouterLink(g.getSlug(), GroupView.class,
                new com.vaadin.flow.router.RouteParameters("slug", g.getSlug())))
                .setHeader("Slug").setAutoWidth(true);
        grid.addColumn(g -> g.getName() == null ? "" : g.getName())
                .setHeader("Name").setAutoWidth(true);
        grid.addColumn(g -> g.getDescription() == null ? "" : g.getDescription())
                .setHeader("Description").setAutoWidth(true);
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
