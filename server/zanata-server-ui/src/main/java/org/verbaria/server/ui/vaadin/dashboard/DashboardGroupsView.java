package org.verbaria.server.ui.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.zanata.model.HIterationGroup;
import org.verbaria.server.headless.repository.IterationGroupRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "dashboard/groups", layout = MainLayout.class)
@AnonymousAllowed
public class DashboardGroupsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.groups"; }


    public DashboardGroupsView(IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2(getTranslation("dashboardGroups.title"));

        Grid<HIterationGroup> grid = new Grid<>(HIterationGroup.class, false);
        grid.addColumn(HIterationGroup::getName).setHeader(getTranslation("dashboardGroups.colName")).setAutoWidth(true);
        grid.addColumn(HIterationGroup::getSlug).setHeader(getTranslation("dashboardGroups.colSlug")).setAutoWidth(true);
        grid.addColumn(HIterationGroup::getDescription).setHeader(getTranslation("dashboardGroups.colDescription"));
        grid.addColumn(HIterationGroup::getCreationDate).setHeader(getTranslation("dashboardGroups.colCreated")).setAutoWidth(true);

        CallbackDataProvider<HIterationGroup, Void> dp = DataProvider.fromCallbacks(
                q -> {
                    int page = q.getOffset() / Math.max(1, q.getLimit());
                    return groupRepository.search("",
                                    PageRequest.of(page, q.getLimit()))
                            .stream();
                },
                q -> (int) Math.min(Integer.MAX_VALUE, groupRepository.count()));
        grid.setDataProvider(dp);
        grid.setHeight("70vh");

        add(heading, grid);
    }
}
