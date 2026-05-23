package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.model.HIterationGroup;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard/groups", layout = MainLayout.class)
@PageTitle("Groups | Zanata")
@AnonymousAllowed
public class DashboardGroupsView extends VerticalLayout {

    public DashboardGroupsView(IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("Groups");

        Grid<HIterationGroup> grid = new Grid<>(HIterationGroup.class, false);
        grid.addColumn(HIterationGroup::getName).setHeader("Name").setAutoWidth(true);
        grid.addColumn(HIterationGroup::getSlug).setHeader("Slug").setAutoWidth(true);
        grid.addColumn(HIterationGroup::getDescription).setHeader("Description");
        grid.addColumn(HIterationGroup::getCreationDate).setHeader("Created").setAutoWidth(true);
        grid.setItems(groupRepository.findAll());

        add(heading, grid);
    }
}
