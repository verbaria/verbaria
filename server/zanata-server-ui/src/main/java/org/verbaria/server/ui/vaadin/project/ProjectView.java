package org.verbaria.server.ui.vaadin.project;

import org.verbaria.server.headless.security.Roles;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.tabs.TabSheetVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;
import org.verbaria.server.ui.vaadin.theme.ProgressBars;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;

import org.zanata.model.HLocale;
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
import org.verbaria.server.ui.vaadin.HasToolbarActions;
import org.verbaria.server.ui.vaadin.HasToolbarSubtitle;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.headless.stats.IterationStats;

@Route(value = "project/view/:slug", layout = MainLayout.class)
@AnonymousAllowed
public class ProjectView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs, HasToolbarActions, HasToolbarSubtitle {

    @Override public String pageTitleKey() { return "page.project"; }

    @Override public Component toolbarActions() { return toolbarActions; }

    @Override public Component toolbarSubtitle() { return toolbarSubtitle; }

    private Component toolbarActions;
    private Component toolbarSubtitle;


    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final PersonRepository personRepository;
    private final ProjectMembershipService membershipService;
    private final BreadcrumbsService breadcrumbsService;

    private String currentSlug;
    private String currentVersionSlug;

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

        List<HProjectIteration> iterations = new ArrayList<>(project.getProjectIterations());
        iterations.sort(Comparator
                .comparing((HProjectIteration i) -> i.getStatus() == EntityStatus.READONLY ? 0 : 1)
                .thenComparing(HProjectIteration::getSlug, Comparator.reverseOrder()));

        ComboBox<HProjectIteration> versionBox = new ComboBox<>();
        versionBox.setItems(iterations);
        versionBox.setItemLabelGenerator(HProjectIteration::getSlug);
        versionBox.setWidth("150px");
        versionBox.setAllowCustomValue(false);
        versionBox.setPlaceholder(getTranslation("project.version"));

        this.toolbarActions = buildToolbarActions(slug, versionBox);
        this.toolbarSubtitle = buildSubtitle(project);

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

        Grid<IterationStats.LocaleStats> languages = buildLanguageGrid(slug);
        if (iterations.isEmpty()) {
            versionBox.setEnabled(false);
        } else {
            HProjectIteration initial = iterations.stream()
                    .filter(it -> "master".equals(it.getSlug()))
                    .findFirst().orElse(iterations.get(0));
            versionBox.setValue(initial);
            renderLanguages(languages, initial);
            versionBox.addValueChangeListener(e -> {
                if (e.getValue() != null) {
                    renderLanguages(languages, e.getValue());
                }
            });
        }

        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();
        tabs.addThemeVariants(TabSheetVariant.AURA_NO_BORDER);
        tabs.addClassNames("fill-tabsheet", AuraUtility.MinHeight.NONE);
        tabs.add(new Tab(getTranslation("project.tab.languages")),
                wrapLanguagesPanel(slug, languages));
        tabs.add(tabWithBadge(getTranslation("project.tab.people"), people.size()),
                buildPeoplePanel(people, rolesByPerson));
        tabs.add(new Tab(getTranslation("project.tab.glossary")), buildGlossaryPanel(slug));
        tabs.add(new Tab(getTranslation("project.tab.about")), buildAboutPanel(project));
        addAndExpand(tabs);
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
                // "Projects" links to the projects index; the project's own
                // slug is the current-page leaf.
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/projects"),
                BreadcrumbsService.Crumb.here(slug)
        );
    }

    private Component buildSubtitle(HProject project) {
        String desc = project.getDescription();
        if (desc == null || desc.isBlank()) {
            return null;
        }
        Span span = new Span(desc);
        span.getElement().setAttribute("title", desc);
        return span;
    }

    private Component buildToolbarActions(String slug,
                                          ComboBox<HProjectIteration> versionBox) {
        HorizontalLayout right = new HorizontalLayout(versionBox);
        right.setAlignItems(FlexComponent.Alignment.CENTER);
        right.setSpacing(true);
        if (canManageProject(slug)) {
            Button settings = new Button(LineAwesomeIcon.COG_SOLID.create(),
                    e -> getUI().ifPresent(ui ->
                            ui.navigate("project/view/" + slug + "/settings")));
            settings.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            settings.getElement().setAttribute("title", getTranslation("page.settings"));
            settings.getElement().setAttribute("aria-label", getTranslation("page.settings"));
            right.add(settings);
        }
        return right;
    }

    private void openAddLanguageDialog(String slug) {
        HProject project = projectRepository.findBySlugWithLocales(slug).orElse(null);
        if (project == null) {
            return;
        }
        Set<HLocale> current = project.getCustomizedLocales() == null
                ? Set.of() : project.getCustomizedLocales();
        List<HLocale> available = localeRepository.findAll().stream()
                .filter(l -> !current.contains(l))
                .sorted(Comparator.comparing(ProjectView::localeLabel,
                        String.CASE_INSENSITIVE_ORDER))
                .toList();

        ComboBox<HLocale> picker = new ComboBox<>();
        picker.setItems(available);
        picker.setItemLabelGenerator(ProjectView::localeLabel);
        picker.setWidthFull();
        picker.setPlaceholder(getTranslation("project.addLanguage"));

        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("project.addLanguage"));
        dlg.add(picker);
        Button add = new Button(getTranslation("common.add"), e -> {
            HLocale picked = picker.getValue();
            if (picked == null) {
                picker.setInvalid(true);
                return;
            }
            addLanguage(slug, picked);
            dlg.close();
            UI.getCurrent().getPage().reload();
        });
        add.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        dlg.getFooter().add(cancel, add);
        dlg.open();
    }

    private void addLanguage(String slug, HLocale locale) {
        projectRepository.findBySlugWithLocales(slug).ifPresent(project -> {
            Set<HLocale> set = new LinkedHashSet<>();
            if (project.getCustomizedLocales() != null) {
                set.addAll(project.getCustomizedLocales());
            }
            set.add(locale);
            project.setCustomizedLocales(set);
            project.setOverrideLocales(true);
            projectRepository.save(project);
        });
    }

    private static String localeLabel(HLocale l) {
        if (l == null) {
            return "";
        }
        String code = l.getLocaleId() == null ? "?" : l.getLocaleId().getId();
        String display = l.getDisplayName();
        return display == null || display.isBlank() ? code
                : display + " (" + code + ")";
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

    private Component wrapLanguagesPanel(String slug,
            Grid<IterationStats.LocaleStats> grid) {
        if (!canManageProject(slug)) {
            return grid;
        }
        VerticalLayout panel = new VerticalLayout();
        panel.setSizeFull();
        panel.setPadding(false);
        panel.setSpacing(false);
        panel.addClassNames(AuraUtility.MinHeight.NONE);

        Button addLang = new Button(getTranslation("project.addLanguage"),
                LineAwesomeIcon.PLUS_SOLID.create(),
                e -> openAddLanguageDialog(slug));
        addLang.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        HorizontalLayout bar = new HorizontalLayout(addLang);
        bar.setWidthFull();
        bar.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        bar.addClassNames(AuraUtility.Padding.SMALL);

        panel.add(bar);
        panel.addAndExpand(grid);
        return panel;
    }

    private Grid<IterationStats.LocaleStats> buildLanguageGrid(String projectSlug) {
        Grid<IterationStats.LocaleStats> grid = new Grid<>();
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.NONE);
        grid.addClassNames(AuraUtility.MinHeight.NONE);
        grid.addThemeVariants(GridVariant.ROW_STRIPES);
        grid.addColumn(new ComponentRenderer<>(this::languageNameCell))
                .setHeader(getTranslation("project.tab.languages")).setFlexGrow(1);
        grid.addColumn(new ComponentRenderer<>(this::languagePctCell))
                .setHeader(getTranslation("iteration.stats.translated"))
                .setFlexGrow(1);
        grid.addItemClickListener(e -> {
            String localeIdStr = localeIdOf(e.getItem());
            if (!localeIdStr.isBlank() && currentVersionSlug != null) {
                getUI().ifPresent(ui -> ui.navigate("translate/" + projectSlug
                        + "/" + currentVersionSlug + "/" + localeIdStr));
            }
        });
        return grid;
    }

    private void renderLanguages(Grid<IterationStats.LocaleStats> grid,
                                 HProjectIteration iter) {
        this.currentVersionSlug = iter.getSlug();
        IterationStats stats = IterationStats.compute(iter.getId(),
                iterationRepository, targetRepository, localeRepository);
        grid.setItems(stats.perLocale.stream()
                .sorted(Comparator.comparing(ProjectView::localeIdOf,
                        String.CASE_INSENSITIVE_ORDER))
                .toList());
    }

    private Component languageNameCell(IterationStats.LocaleStats ls) {
        String localeIdStr = localeIdOf(ls);
        String display = ls.locale.getDisplayName();
        if (display == null || display.isBlank()) {
            display = localeIdStr;
        }
        Span name = new Span(localeIdStr.isBlank() ? display
                : display + " (" + localeIdStr + ")");
        name.addClassNames(AuraUtility.FontWeight.SEMIBOLD,
                AuraUtility.Cursor.POINTER);
        return name;
    }

    private Component languagePctCell(IterationStats.LocaleStats ls) {
        VerticalLayout cell = new VerticalLayout();
        cell.setPadding(false);
        cell.setSpacing(false);
        Span pct = new Span(String.format("%.0f%%", ls.translatedPct));
        pct.addClassNames(AuraUtility.FontSize.SMALL,
                ProgressBars.textColorClass(ls.translatedPct));
        ProgressBar bar = ProgressBars.translated(ls.translatedPct);
        bar.setWidthFull();
        cell.add(pct, bar);
        return cell;
    }

    private static String localeIdOf(IterationStats.LocaleStats ls) {
        return ls.locale.getLocaleId() == null ? ""
                : ls.locale.getLocaleId().getId();
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
