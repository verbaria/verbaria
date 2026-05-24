package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard", layout = MainLayout.class)
@PageTitle("Dashboard | Zanata")
@PermitAll
public class DashboardHomeView extends VerticalLayout {

    public DashboardHomeView(ProjectRepository projectRepository,
                             IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("Dashboard"));

        HorizontalLayout stats = new HorizontalLayout();
        stats.setSpacing(true);
        stats.add(statCard("Projects", projectRepository.count()));
        stats.add(statCard("Groups", groupRepository.count()));
        add(stats);

        add(new H3("Quick links"));
        Div links = new Div();
        links.add(linkCard("Projects",  LineAwesomeIcon.FOLDER, DashboardProjectsView.class));
        links.add(linkCard("Activity",  LineAwesomeIcon.HISTORY_SOLID, DashboardActivityView.class));
        links.add(linkCard("Groups",    LineAwesomeIcon.USERS_SOLID, DashboardGroupsView.class));
        links.add(linkCard("Settings",  LineAwesomeIcon.COG_SOLID, DashboardSettingsView.class));
        links.getStyle().set("display", "flex");
        links.getStyle().set("flex-wrap", "wrap");
        links.getStyle().set("gap", "0.75rem");
        add(links);
    }

    private Div statCard(String label, long value) {
        Div card = new Div();
        card.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        card.getStyle().set("border-radius", "8px");
        card.getStyle().set("padding", "1rem 1.5rem");
        card.getStyle().set("min-width", "180px");
        H2 num = new H2(String.valueOf(value));
        num.getStyle().set("margin", "0");
        num.getStyle().set("font-size", "1.75rem");
        Span lbl = new Span(label);
        lbl.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        lbl.getStyle().set("font-size", "0.875rem");
        VerticalLayout layout = new VerticalLayout(num, lbl);
        layout.setPadding(false);
        layout.setSpacing(false);
        card.add(layout);
        return card;
    }

    private RouterLink linkCard(String label, LineAwesomeIcon icon, Class<? extends com.vaadin.flow.component.Component> target) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RouterLink link = new RouterLink(label, (Class) target);
        link.getStyle().set("display", "inline-flex");
        link.getStyle().set("align-items", "center");
        link.getStyle().set("gap", "0.5rem");
        link.getStyle().set("padding", "0.65rem 1rem");
        link.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        link.getStyle().set("border-radius", "8px");
        link.getStyle().set("text-decoration", "none");
        link.getStyle().set("color", "var(--vaadin-text-color)");
        var ico = icon.create();
        ico.getStyle().set("font-size", "1rem");
        ico.getStyle().set("color", "var(--vaadin-color-primary)");
        HorizontalLayout row = new HorizontalLayout(ico, new Span(label));
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        link.add(row);
        return link;
    }
}
