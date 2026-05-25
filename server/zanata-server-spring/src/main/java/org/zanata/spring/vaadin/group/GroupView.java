package org.zanata.spring.vaadin.group;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.zanata.model.HIterationGroup;
import org.zanata.model.HPerson;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "group/view/:slug", layout = MainLayout.class)
@AnonymousAllowed
public class GroupView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.group"; }


    private final IterationGroupRepository groupRepository;

    public GroupView(IterationGroupRepository groupRepository) {
        this.groupRepository = groupRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HIterationGroup group = groupRepository.findBySlugWithFetch(slug)
                .orElseThrow(() -> new NotFoundException("Group not found: " + slug));

        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb(getTranslation("translate.breadcrumb.home"), "/"),
                new Breadcrumb(getTranslation("nav.groups"), "/groups"),
                new Breadcrumb(slug, "#", true));
        add(crumbs);

        H1 name = new H1(group.getName() == null || group.getName().isBlank()
                ? slug : group.getName());
        name.addClassNames(LumoUtility.Margin.NONE);
        add(name);

        if (group.getDescription() != null && !group.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(group.getDescription());
            desc.getStyle().set("font-style", "italic");
            add(desc);
        }

        List<HProjectIteration> versions = new ArrayList<>(group.getProjectIterations());
        versions.sort(Comparator.comparing(
                i -> i.getProject() == null ? "" : i.getProject().getSlug(),
                String.CASE_INSENSITIVE_ORDER));
        Grid<HProjectIteration> versionGrid = new Grid<>(HProjectIteration.class, false);
        versionGrid.addColumn(i -> i.getProject() == null ? "" : i.getProject().getSlug())
                .setHeader(getTranslation("groupView.colProject")).setAutoWidth(true);
        versionGrid.addColumn(HProjectIteration::getSlug)
                .setHeader(getTranslation("groupView.colVersion")).setAutoWidth(true);
        versionGrid.addColumn(i -> i.getStatus() == null ? "" : i.getStatus().name())
                .setHeader(getTranslation("groupView.colStatus")).setAutoWidth(true);
        versionGrid.setItems(versions);
        versionGrid.setAllRowsVisible(true);
        add(new Paragraph(getTranslation("groupView.versionsHeading", versions.size())));
        add(versionGrid);

        List<HPerson> maintainers = new ArrayList<>(group.getMaintainers());
        maintainers.sort(Comparator.comparing(
                p -> p.getName() == null ? "" : p.getName(),
                String.CASE_INSENSITIVE_ORDER));
        Grid<HPerson> peopleGrid = new Grid<>(HPerson.class, false);
        peopleGrid.addColumn(p -> p.getName() == null ? "" : p.getName())
                .setHeader(getTranslation("groupView.colName")).setAutoWidth(true);
        peopleGrid.addColumn(p -> p.getEmail() == null ? "" : p.getEmail())
                .setHeader(getTranslation("groupView.colEmail")).setAutoWidth(true);
        peopleGrid.setItems(maintainers);
        peopleGrid.setAllRowsVisible(true);
        add(new Paragraph(getTranslation("groupView.maintainersHeading", maintainers.size())));
        add(peopleGrid);
    }
}
