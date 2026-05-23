package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | Zanata")
@AnonymousAllowed
public class DashboardHomeView extends VerticalLayout {

    public DashboardHomeView(ProjectRepository projectRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("Dashboard");
        long projectCount = projectRepository.count();
        Paragraph counts = new Paragraph("Projects: " + projectCount);

        Tabs tabs = new Tabs(
                new Tab(new RouterLink("Projects", DashboardProjectsView.class)),
                new Tab(new RouterLink("Activity", DashboardActivityView.class)),
                new Tab(new RouterLink("Groups", DashboardGroupsView.class)),
                new Tab(new RouterLink("Settings", DashboardSettingsView.class))
        );

        add(heading, counts, tabs);
    }
}
