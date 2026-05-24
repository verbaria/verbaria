package org.zanata.spring.vaadin.project;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;

import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.vaadin.ExploreView;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.iteration.IterationView;
import org.zanata.spring.vaadin.stats.IterationStats;

@Route(value = "project/view/:slug", layout = MainLayout.class)
@PageTitle("Project | Zanata")
@AnonymousAllowed
public class ProjectView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;
    private final org.zanata.spring.repository.AccountRepository accountRepository;

    public ProjectView(ProjectRepository projectRepository,
                       ProjectIterationRepository iterationRepository,
                       TextFlowTargetRepository targetRepository,
                       LocaleRepository localeRepository,
                       org.zanata.spring.repository.AccountRepository accountRepository) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private boolean canManageProject(String projectSlug) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return false;
        }
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (admin) return true;
        return accountRepository.findByUsername(auth.getName())
                .map(a -> a.getPerson())
                .flatMap(p -> projectRepository.findBySlugWithIterations(projectSlug)
                        .map(proj -> proj.getMaintainers().contains(p)))
                .orElse(false);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HProject project = projectRepository.findBySlugWithIterations(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));

        add(buildBreadcrumb());
        add(buildHeading(project, slug));
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(project.getDescription());
            desc.getStyle().set("font-style", "italic");
            desc.addClassNames(LumoUtility.Margin.Top.NONE);
            add(desc);
        }

        List<HProjectIteration> iterations = new ArrayList<>(project.getProjectIterations());
        iterations.sort(Comparator
                .comparing((HProjectIteration i) -> i.getStatus() == EntityStatus.READONLY ? 0 : 1)
                .thenComparing(HProjectIteration::getSlug, Comparator.reverseOrder()));

        List<HPerson> maintainers = new ArrayList<>(project.getMaintainers());
        maintainers.sort(Comparator.comparing(
                p -> p.getName() == null ? "" : p.getName(),
                String.CASE_INSENSITIVE_ORDER));

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(tabWithBadge("Versions", iterations.size()),
                buildVersionsPanel(slug, iterations));
        tabs.add(tabWithBadge("People", maintainers.size()),
                buildPeoplePanel(maintainers));
        tabs.add(new Tab("About"), buildAboutPanel(project));
        add(tabs);
    }

    private Breadcrumbs buildBreadcrumb() {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore", true)
        );
        return crumbs;
    }

    private HorizontalLayout buildHeading(HProject project, String slug) {
        H1 name = new H1(project.getName() == null || project.getName().isBlank()
                ? slug : project.getName());
        name.addClassNames(LumoUtility.Margin.NONE);
        HorizontalLayout layout = new HorizontalLayout(name);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        return layout;
    }

    private Tab tabWithBadge(String label, int count) {
        Span badge = new Span(String.valueOf(count));
        badge.getElement().getThemeList().add("badge contrast small");
        badge.getStyle().set("margin-inline-start", "0.5rem");
        return new Tab(new Span(label), badge);
    }

    private Div buildVersionsPanel(String projectSlug, List<HProjectIteration> iterations) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");

        com.vaadin.flow.component.select.Select<String> sort = new com.vaadin.flow.component.select.Select<>();
        sort.setItems("Slug (Z-A)", "Slug (A-Z)", "Status", "Translated % (desc)", "Approved % (desc)");
        sort.setValue("Slug (Z-A)");
        sort.setWidth("220px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3("Versions");
        title.addClassNames(LumoUtility.Margin.NONE);

        HorizontalLayout right = new HorizontalLayout();
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);
        if (canManageProject(projectSlug)) {
            com.vaadin.flow.component.button.Button addVersion =
                    new com.vaadin.flow.component.button.Button("New version",
                            LineAwesomeIcon.PLUS_SOLID.create(),
                            e -> getUI().ifPresent(ui -> ui.navigate(
                                    "project/" + projectSlug + "/add-version")));
            addVersion.addThemeVariants(
                    com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY,
                    com.vaadin.flow.component.button.ButtonVariant.LUMO_SMALL);
            right.add(addVersion);
        }
        right.add(new Span("Sort"), sort);
        header.add(title, right);
        panel.add(header);

        Span counter = new Span("v " + iterations.size());
        counter.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        counter.getStyle().set("display", "block");
        counter.getStyle().set("margin", "0.5rem 0 0.75rem 0");
        panel.add(counter);

        Div listContainer = new Div();
        listContainer.setWidthFull();
        panel.add(listContainer);
        renderVersionList(listContainer, projectSlug, iterations, sort.getValue());
        sort.addValueChangeListener(e ->
                renderVersionList(listContainer, projectSlug, iterations, e.getValue()));
        return panel;
    }

    private void renderVersionList(Div container, String projectSlug,
                                   List<HProjectIteration> iterations, String sortBy) {
        container.removeAll();
        java.util.Comparator<HProjectIteration> cmp = switch (sortBy == null ? "" : sortBy) {
            case "Slug (A-Z)" -> java.util.Comparator
                    .comparing(HProjectIteration::getSlug, String.CASE_INSENSITIVE_ORDER);
            case "Status" -> java.util.Comparator
                    .comparing((HProjectIteration i) -> i.getStatus() == null
                            ? "" : i.getStatus().name())
                    .thenComparing(HProjectIteration::getSlug, String.CASE_INSENSITIVE_ORDER);
            case "Translated % (desc)" -> java.util.Comparator
                    .comparingDouble((HProjectIteration i) -> -IterationStats
                            .compute(i.getId(), iterationRepository, targetRepository, localeRepository)
                            .translatedPct);
            case "Approved % (desc)" -> java.util.Comparator
                    .comparingDouble((HProjectIteration i) -> -IterationStats
                            .compute(i.getId(), iterationRepository, targetRepository, localeRepository)
                            .approvedPct);
            default -> java.util.Comparator
                    .comparing(HProjectIteration::getSlug, java.util.Comparator.reverseOrder());
        };
        iterations.stream().sorted(cmp)
                .forEach(it -> container.add(buildVersionRow(projectSlug, it)));
    }

    private Div buildVersionRow(String projectSlug, HProjectIteration iter) {
        IterationStats stats = IterationStats.compute(iter.getId(),
                iterationRepository, targetRepository, localeRepository);

        RouterLink link = new RouterLink("", IterationView.class,
                new RouteParameters(
                        new RouteParam("projectSlug", projectSlug),
                        new RouteParam("versionSlug", iter.getSlug())));
        link.getStyle().set("text-decoration", "none");
        link.getStyle().set("color", "inherit");
        link.getStyle().set("display", "block");

        Div card = new Div();
        card.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.Border.BOTTOM,
                LumoUtility.BorderColor.CONTRAST_10);
        card.getStyle().set("width", "100%");

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout left = new HorizontalLayout();
        left.setSpacing(true);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        Span slugSpan = new Span(iter.getSlug());
        slugSpan.addClassNames(LumoUtility.FontWeight.BOLD, LumoUtility.FontSize.MEDIUM);
        left.add(slugSpan);
        if (iter.getStatus() == EntityStatus.READONLY) {
            var lock = LineAwesomeIcon.LOCK_SOLID.create();
            lock.setSize("0.9em");
            lock.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            left.add(lock);
        }

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        Span pct = new Span(String.format("%.2f%%", stats.translatedPct));
        pct.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        pct.getStyle().set("color", "var(--aura-green)");
        Span tag = new Span("translated");
        tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        right.add(pct, tag);

        row.add(left, right);
        card.add(row);

        ProgressBar bar = new ProgressBar(0.0, 1.0, stats.translatedPct / 100.0);
        bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
        bar.getStyle().set("margin-top", "0.4rem");
        card.add(bar);

        link.getElement().appendChild(card.getElement());
        return wrap(link);
    }

    private Div wrap(RouterLink link) {
        Div wrap = new Div(link);
        wrap.getStyle().set("width", "100%");
        return wrap;
    }

    private Div buildPeoplePanel(List<HPerson> maintainers) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");

        H3 title = new H3("Maintainers");
        title.addClassNames(LumoUtility.Margin.NONE);
        panel.add(title);

        Grid<HPerson> grid = new Grid<>(HPerson.class, false);
        grid.addColumn(p -> p.getName() == null ? "" : p.getName())
                .setHeader("Name").setAutoWidth(true);
        grid.addColumn(p -> p.getEmail() == null ? "" : p.getEmail())
                .setHeader("Email").setAutoWidth(true);
        grid.addColumn(p -> p.getAccount() == null || p.getAccount().getUsername() == null
                ? "" : p.getAccount().getUsername())
                .setHeader("Username").setAutoWidth(true);
        grid.setItems(maintainers);
        grid.setAllRowsVisible(true);
        panel.add(grid);
        return panel;
    }

    private Div buildAboutPanel(HProject project) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");

        String home = project.getHomeContent();
        if (home == null || home.isBlank()) {
            Paragraph empty = new Paragraph("No content");
            empty.addClassNames(LumoUtility.TextColor.SECONDARY);
            panel.add(empty);
        } else {
            Paragraph p = new Paragraph(home);
            p.getStyle().set("white-space", "pre-wrap");
            panel.add(p);
        }
        return panel;
    }
}
