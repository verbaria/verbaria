package org.verbaria.server.ui.vaadin.project;

import org.verbaria.server.headless.security.Roles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.select.Select;

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
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;
import org.verbaria.server.ui.vaadin.theme.ProgressBars;
import org.verbaria.server.ui.vaadin.theme.SourceLinks;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;

import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HProjectMember;
import org.zanata.model.ProjectRole;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.PersonRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.service.ProjectMembershipService;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.ExploreView;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.ui.vaadin.iteration.IterationView;
import org.verbaria.server.headless.stats.IterationStats;

@Route(value = "project/view/:slug", layout = MainLayout.class)
@AnonymousAllowed
public class ProjectView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.project"; }


    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final ProjectMembershipService membershipService;
    private final BreadcrumbsService breadcrumbsService;

    private String currentSlug;

    public ProjectView(ProjectRepository projectRepository,
                       ProjectIterationRepository iterationRepository,
                       TextFlowTargetRepository targetRepository,
                       LocaleRepository localeRepository,
                       AccountRepository accountRepository,
                       PersonRepository personRepository,
                       ProjectMembershipService membershipService,
                       BreadcrumbsService breadcrumbsService) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
        this.personRepository = personRepository;
        this.membershipService = membershipService;
        this.breadcrumbsService = breadcrumbsService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private boolean canManageProject(String projectSlug) {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (Roles.isAdmin(auth)) return true;
        if (auth == null) return false;
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
        this.currentSlug = slug;
        HProject project = projectRepository.findBySlugWithIterations(slug)
                .orElseThrow(() -> new NotFoundException("Project not found: " + slug));
        // Reload with members + person.account eagerly attached so the People
        // panel can render usernames without lazy-init hits.
        HProject withMembers = projectRepository.findBySlugWithMembers(slug)
                .orElse(project);

        publishBreadcrumb(slug);
        add(buildHeading(project, slug));
        if (project.getDescription() != null && !project.getDescription().isBlank()) {
            Paragraph desc = new Paragraph(project.getDescription());
            desc.addClassNames(AuraUtility.Margin.Top.NONE, AuraUtility.FontStyle.ITALIC);
            add(desc);
        }

        List<HProjectIteration> iterations = new ArrayList<>(project.getProjectIterations());
        iterations.sort(Comparator
                .comparing((HProjectIteration i) -> i.getStatus() == EntityStatus.READONLY ? 0 : 1)
                .thenComparing(HProjectIteration::getSlug, Comparator.reverseOrder()));

        // Person → roles aggregation for the People panel.
        Map<HPerson, EnumSet<ProjectRole>> rolesByPerson =
                new LinkedHashMap<>();
        for (HProjectMember m : withMembers.getMembers()) {
            if (m.getPerson() == null) continue;
            rolesByPerson.computeIfAbsent(m.getPerson(),
                    k -> EnumSet.noneOf(ProjectRole.class)).add(m.getRole());
        }
        List<HPerson> people = new ArrayList<>(rolesByPerson.keySet());
        people.sort(Comparator.comparing(
                p -> p.getName() == null ? "" : p.getName(),
                String.CASE_INSENSITIVE_ORDER));

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(tabWithBadge(getTranslation("project.tab.versions"), iterations.size()),
                buildVersionsPanel(slug, iterations));
        tabs.add(tabWithBadge(getTranslation("project.tab.people"), people.size()),
                buildPeoplePanel(people, rolesByPerson));
        tabs.add(new Tab(getTranslation("project.tab.glossary")), buildGlossaryPanel(slug));
        tabs.add(new Tab(getTranslation("project.tab.about")), buildAboutPanel(project));
        add(tabs);
    }

    /**
     * Project-scoped glossary panel. Full CRUD is a separate epic; for now
     * this surfaces the relationship and links out to the system glossary
     * view filtered for entries qualified as {@code project/<slug>}, which
     * is the legacy convention.
     */
    private Div buildGlossaryPanel(String slug) {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER, AuraUtility.Width.FULL);

        H3 title = new H3(getTranslation("project.tab.glossary"));
        title.addClassNames(AuraUtility.Margin.NONE);
        panel.add(title);

        Paragraph intro = new Paragraph(getTranslation("project.glossary.intro"));
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Vertical.SMALL);
        panel.add(intro);

        Paragraph empty = new Paragraph(getTranslation("project.glossary.empty"));
        empty.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Vertical.XSMALL);
        panel.add(empty);

        Anchor systemGlossary = new Anchor("/glossary", getTranslation("project.glossary.systemLink"));
        systemGlossary.addClassNames(AuraUtility.TextColor.PRIMARY);
        panel.add(systemGlossary);
        return panel;
    }

    private void publishBreadcrumb(String slug) {
        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                // "Projects" links to the explore index; the project's own
                // slug is the current-page leaf.
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/explore"),
                BreadcrumbsService.Crumb.here(slug)
        );
    }

    private HorizontalLayout buildHeading(HProject project, String slug) {
        H1 name = new H1(project.getName() == null || project.getName().isBlank()
                ? slug : project.getName());
        name.addClassNames(AuraUtility.Margin.NONE);
        HorizontalLayout left = new HorizontalLayout(name);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        left.setSpacing(true);
        Anchor source = SourceLinks.of(project.getSourceViewURL());
        if (source != null) {
            source.addClassNames(AuraUtility.FontSize.LARGE);
            left.addComponentAsFirst(source);
        }

        HorizontalLayout layout = new HorizontalLayout(left);
        layout.setWidthFull();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (canManageProject(slug)) {
            Button settings = new Button(getTranslation("page.settings"),
                    LineAwesomeIcon.COG_SOLID.create(),
                    e -> getUI().ifPresent(ui ->
                            ui.navigate("project/view/" + slug + "/settings")));
            settings.addThemeVariants(ButtonVariant.TERTIARY);
            layout.add(settings);
        }
        return layout;
    }

    private Tab tabWithBadge(String label, int count) {
        com.vaadin.flow.component.badge.Badge badge =
                new com.vaadin.flow.component.badge.Badge();
        badge.setNumber(count);
        badge.addThemeVariants(
                com.vaadin.flow.component.badge.BadgeVariant.CONTRAST,
                com.vaadin.flow.component.badge.BadgeVariant.SMALL);
        badge.addClassNames(AuraUtility.Margin.Start.SMALL);
        return new Tab(new Span(label), badge);
    }

    private Div buildVersionsPanel(String projectSlug, List<HProjectIteration> iterations) {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER, AuraUtility.Width.FULL);

        Select<String> sort = new Select<>();
        sort.setItems("Slug (Z-A)", "Slug (A-Z)", "Status", "Translated % (desc)", "Approved % (desc)");
        sort.setValue("Slug (Z-A)");
        sort.setWidth("220px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3("Versions");
        title.addClassNames(AuraUtility.Margin.NONE);

        HorizontalLayout right = new HorizontalLayout();
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);
        if (canManageProject(projectSlug)) {
            Button addVersion =
                    new Button("New version",
                            LineAwesomeIcon.PLUS_SOLID.create(),
                            e -> getUI().ifPresent(ui -> ui.navigate(
                                    "project/" + projectSlug + "/add-version")));
            addVersion.addThemeVariants(
                    ButtonVariant.PRIMARY,
                    ButtonVariant.SMALL);
            right.add(addVersion);
        }
        right.add(new Span("Sort"), sort);
        header.add(title, right);
        header.addClassNames(AuraUtility.Margin.Bottom.MEDIUM);
        panel.add(header);

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
        Comparator<HProjectIteration> cmp = switch (sortBy == null ? "" : sortBy) {
            case "Slug (A-Z)" -> Comparator
                    .comparing(HProjectIteration::getSlug, String.CASE_INSENSITIVE_ORDER);
            case "Status" -> Comparator
                    .comparing((HProjectIteration i) -> i.getStatus() == null
                            ? "" : i.getStatus().name())
                    .thenComparing(HProjectIteration::getSlug, String.CASE_INSENSITIVE_ORDER);
            case "Translated % (desc)" -> Comparator
                    .comparingDouble((HProjectIteration i) -> -IterationStats
                            .compute(i.getId(), iterationRepository, targetRepository, localeRepository)
                            .translatedPct);
            case "Approved % (desc)" -> Comparator
                    .comparingDouble((HProjectIteration i) -> -IterationStats
                            .compute(i.getId(), iterationRepository, targetRepository, localeRepository)
                            .approvedPct);
            default -> Comparator
                    .comparing(HProjectIteration::getSlug, Comparator.reverseOrder());
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
        link.addClassNames(AuraUtility.TextDecoration.NONE, AuraUtility.Display.BLOCK);
        link.getStyle().set("color", "inherit");

        Div card = new Div();
        card.addClassNames(AuraUtility.Padding.SMALL, AuraUtility.Border.BOTTOM,
                AuraUtility.BorderColor.SECONDARY, AuraUtility.BoxSizing.BORDER, AuraUtility.Width.FULL);

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        HorizontalLayout left = new HorizontalLayout();
        left.setSpacing(true);
        left.setAlignItems(FlexComponent.Alignment.CENTER);
        Span slugSpan = new Span(iter.getSlug());
        slugSpan.addClassNames(AuraUtility.FontWeight.BOLD, AuraUtility.FontSize.MEDIUM);
        left.add(slugSpan);
        if (iter.getStatus() == EntityStatus.READONLY) {
            var lock = LineAwesomeIcon.LOCK_SOLID.create();
            lock.setSize("0.9em");
            lock.addClassNames(AuraUtility.TextColor.SECONDARY);
            left.add(lock);
        }

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        Span pct = new Span(String.format("%.2f%%", stats.translatedPct));
        pct.addClassNames(AuraUtility.FontSize.LARGE, AuraUtility.FontWeight.BOLD,
                ProgressBars.textColorClass(stats.translatedPct));
        Span tag = new Span("translated");
        tag.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.TextColor.SECONDARY);
        right.add(pct, tag);

        row.add(left, right);
        card.add(row);

        ProgressBar bar = ProgressBars.translated(stats.translatedPct);
        bar.addClassNames(AuraUtility.Margin.Top.SMALL);
        card.add(bar);

        link.getElement().appendChild(card.getElement());
        return wrap(link);
    }

    private Div wrap(RouterLink link) {
        Div wrap = new Div(link);
        wrap.addClassNames(AuraUtility.Width.FULL);
        return wrap;
    }

    private Div buildPeoplePanel(List<HPerson> people,
                                 Map<HPerson, EnumSet<ProjectRole>> rolesByPerson) {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER, AuraUtility.Width.FULL);

        boolean canManage = canManageProject(currentSlug);

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3("Project team");
        title.addClassNames(AuraUtility.Margin.NONE);
        header.add(title);
        if (canManage) {
            Button add = new Button(
                    "Add someone",
                    LineAwesomeIcon.USER_PLUS_SOLID.create(),
                    e -> openAddPersonDialog());
            add.addThemeVariants(ButtonVariant.PRIMARY);
            header.add(add);
        }
        panel.add(header);

        Grid<HPerson> grid = new Grid<>(HPerson.class, false);
        grid.addColumn(p -> p.getName() == null ? "" : p.getName())
                .setHeader("Name").setAutoWidth(true);
        grid.addColumn(p -> p.getAccount() == null || p.getAccount().getUsername() == null
                ? "" : p.getAccount().getUsername())
                .setHeader("Username").setAutoWidth(true);
        grid.addColumn(p -> p.getEmail() == null ? "" : p.getEmail())
                .setHeader("Email").setAutoWidth(true);
        grid.addColumn(p -> {
            var rs = rolesByPerson.getOrDefault(p, EnumSet.noneOf(ProjectRole.class));
            return rs.isEmpty() ? "—"
                    : rs.stream().map(Enum::name).collect(Collectors.joining(", "));
        }).setHeader("Roles").setAutoWidth(true);
        if (canManage) {
            grid.addComponentColumn(p -> {
                Button manage =
                        new Button("Manage permissions",
                                LineAwesomeIcon.USER_COG_SOLID.create(),
                                e -> openManagePermissionsDialog(p, rolesByPerson.getOrDefault(p,
                                        EnumSet.noneOf(ProjectRole.class))));
                manage.addThemeVariants(
                        ButtonVariant.TERTIARY,
                        ButtonVariant.SMALL);
                return manage;
            }).setHeader("Actions").setAutoWidth(true);
        }
        grid.setItems(people);
        grid.setAllRowsVisible(true);
        panel.add(grid);
        return panel;
    }

    private void openAddPersonDialog() {
        Dialog dlg =
                new Dialog();
        dlg.setHeaderTitle("Add team member");
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);

        ComboBox<HPerson> personPicker =
                new ComboBox<>("Search user");
        personPicker.setItemLabelGenerator(this::personLabel);
        personPicker.setWidthFull();
        personPicker.setItems(query -> personRepository.search(
                        query.getFilter().orElse(""),
                        PageRequest.of(
                                query.getPage(), query.getPageSize()))
                .stream());

        CheckboxGroup<ProjectRole> roleGroup =
                new CheckboxGroup<>("Roles");
        roleGroup.setItems(ProjectRole.values());
        roleGroup.setValue(Set.of(ProjectRole.Maintainer));
        roleGroup.setHelperText(
                "Maintainer = full control. TranslationMaintainer = manage translators only.");

        Button cancel = new Button(
                "Cancel", e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        Button save = new Button(
                "Add person", e -> {
                    if (personPicker.getValue() == null) {
                        personPicker.setInvalid(true);
                        personPicker.setErrorMessage("Pick a user");
                        return;
                    }
                    if (roleGroup.getValue().isEmpty()) {
                        roleGroup.setInvalid(true);
                        roleGroup.setErrorMessage("Pick at least one role");
                        return;
                    }
                    try {
                        membershipService.addMember(currentSlug,
                                personPicker.getValue().getId(), roleGroup.getValue());
                        Notification.show(
                                "Added " + personLabel(personPicker.getValue()),
                                2500, Notification.Position.BOTTOM_END);
                        dlg.close();
                        UI.getCurrent().getPage().reload();
                    } catch (Exception ex) {
                        Notification.show(
                                "Failed: " + ex.getMessage(), 4000,
                                Notification.Position.MIDDLE);
                    }
                });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        dlg.add(personPicker, roleGroup);
        dlg.getFooter().add(cancel, save);
        dlg.open();
    }

    private void openManagePermissionsDialog(HPerson person,
                                             EnumSet<ProjectRole> currentRoles) {
        Dialog dlg =
                new Dialog();
        dlg.setHeaderTitle("Manage permissions — " + personLabel(person));
        dlg.setWidth("480px");
        dlg.setCloseOnEsc(true);

        CheckboxGroup<ProjectRole> roleGroup =
                new CheckboxGroup<>("Roles");
        roleGroup.setItems(ProjectRole.values());
        roleGroup.setValue(currentRoles);
        roleGroup.setHelperText(
                "Clearing all roles removes the person from the team. "
                + "Projects must keep at least one Maintainer.");

        Button cancel = new Button(
                "Cancel", e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        Button save = new Button(
                roleGroup.getValue().isEmpty() ? "Remove from team" : "Save permissions",
                e -> {
                    try {
                        membershipService.setProjectRoles(currentSlug,
                                person.getId(), roleGroup.getValue());
                        Notification.show(
                                roleGroup.getValue().isEmpty()
                                        ? "Removed from team" : "Permissions saved",
                                2500, Notification.Position.BOTTOM_END);
                        dlg.close();
                        UI.getCurrent().getPage().reload();
                    } catch (Exception ex) {
                        Notification.show(
                                "Failed: " + ex.getMessage(), 5000,
                                Notification.Position.MIDDLE);
                    }
                });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        roleGroup.addValueChangeListener(ev -> save.setText(
                ev.getValue().isEmpty() ? "Remove from team" : "Save permissions"));

        dlg.add(roleGroup);
        dlg.getFooter().add(cancel, save);
        dlg.open();
    }

    private String personLabel(HPerson p) {
        if (p == null) return "";
        String name = p.getName() == null ? "" : p.getName();
        String username = p.getAccount() != null && p.getAccount().getUsername() != null
                ? p.getAccount().getUsername() : "";
        if (name.isEmpty() && username.isEmpty()) return "(unknown)";
        if (username.isEmpty()) return name;
        if (name.isEmpty()) return username;
        return name + " (" + username + ")";
    }

    private Div buildAboutPanel(HProject project) {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER, AuraUtility.Width.FULL);

        String home = project.getHomeContent();
        if (home == null || home.isBlank()) {
            Paragraph empty = new Paragraph("No content");
            empty.addClassNames(AuraUtility.TextColor.SECONDARY);
            panel.add(empty);
        } else {
            Paragraph p = new Paragraph(home);
            p.addClassNames(AuraUtility.Whitespace.PRE_WRAP);
            panel.add(p);
        }
        return panel;
    }
}
