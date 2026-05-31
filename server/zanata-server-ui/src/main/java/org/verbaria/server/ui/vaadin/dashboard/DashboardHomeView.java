package org.verbaria.server.ui.vaadin.dashboard;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.verbaria.server.headless.repository.IterationGroupRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "dashboard", layout = MainLayout.class)
@PermitAll
public class DashboardHomeView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.dashboard"; }


    public DashboardHomeView(ProjectRepository projectRepository,
                             IterationGroupRepository groupRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2(getTranslation("dashboard.title")));

        HorizontalLayout stats = new HorizontalLayout();
        stats.setSpacing(true);
        stats.add(statCard(getTranslation("dashboard.subnav.projects"), projectRepository.count()));
        stats.add(statCard(getTranslation("dashboard.subnav.groups"), groupRepository.count()));
        add(stats);

        add(new H3(getTranslation("dashboard.title")));
        Div links = new Div();
        links.add(linkCard(getTranslation("dashboard.subnav.projects"),  LineAwesomeIcon.FOLDER, DashboardProjectsView.class));
        links.add(linkCard(getTranslation("dashboard.subnav.activity"),  LineAwesomeIcon.HISTORY_SOLID, DashboardActivityView.class));
        links.add(linkCard(getTranslation("dashboard.subnav.groups"),    LineAwesomeIcon.USERS_SOLID, DashboardGroupsView.class));
        links.add(linkCard(getTranslation("dashboard.subnav.settings"),  LineAwesomeIcon.COG_SOLID, DashboardSettingsView.class));
        links.addClassNames(AuraUtility.Display.FLEX, AuraUtility.FlexWrap.WRAP, AuraUtility.Gap.MEDIUM);
        add(links);
    }

    private Div statCard(String label, long value) {
        Div card = new Div();
        card.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM);
        card.getStyle().set("min-width", "180px");
        H2 num = new H2(String.valueOf(value));
        num.addClassNames(AuraUtility.Margin.NONE, AuraUtility.FontSize.XXLARGE);
        Span lbl = new Span(label);
        lbl.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);
        VerticalLayout layout = new VerticalLayout(num, lbl);
        layout.setPadding(false);
        layout.setSpacing(false);
        card.add(layout);
        return card;
    }

    private RouterLink linkCard(String label, LineAwesomeIcon icon, Class<? extends com.vaadin.flow.component.Component> target) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RouterLink link = new RouterLink(label, (Class) target);
        link.addClassNames(AuraUtility.Display.INLINE_FLEX, AuraUtility.AlignItems.CENTER, AuraUtility.Gap.SMALL,
                AuraUtility.Padding.Vertical.SMALL, AuraUtility.Padding.Horizontal.LARGE, AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.TextDecoration.NONE, AuraUtility.TextColor.BODY);
        var ico = icon.create();
        ico.addClassNames(AuraUtility.FontSize.MEDIUM);
        ico.getStyle().set("color", "var(--vaadin-color-primary)");
        HorizontalLayout row = new HorizontalLayout(ico, new Span(label));
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        link.add(row);
        return link;
    }
}
