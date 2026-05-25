package org.zanata.spring.vaadin.iteration;

import java.util.List;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
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
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;
import org.zanata.model.HDocument;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.vaadin.ExploreView;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.project.ProjectView;
import org.zanata.spring.vaadin.stats.IterationStats;

@Route(value = "project/:projectSlug/version/:versionSlug", layout = MainLayout.class)
@RouteAlias(value = "iteration/view/:projectSlug/:versionSlug", layout = MainLayout.class)
@PageTitle("Project version | Zanata")
@AnonymousAllowed
public class IterationView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;

    private String currentProjectSlug;
    private String currentVersionSlug;
    private String currentSourceLocaleId = "en-US";

    private final org.zanata.spring.service.SourceUploadService sourceUploadService;
    private final org.zanata.spring.service.TranslationUploadService translationUploadService;
    private final org.zanata.spring.service.CopyTransService copyTransService;
    private final org.zanata.spring.service.MergeTransService mergeTransService;
    private final org.zanata.spring.repository.ProjectRepository projectRepository;
    private final org.zanata.spring.vaadin.ProgressDialogService progressDialogs;
    private final org.zanata.spring.repository.AccountRepository accountRepository;

    /** Enabled locales for the current iteration — populated in beforeEnter for the upload dialog. */
    private java.util.List<org.zanata.model.HLocale> enabledLocales = java.util.List.of();

    public IterationView(ProjectIterationRepository iterationRepository,
                         DocumentRepository documentRepository,
                         TextFlowTargetRepository targetRepository,
                         LocaleRepository localeRepository,
                         org.zanata.spring.service.SourceUploadService sourceUploadService,
                         org.zanata.spring.service.TranslationUploadService translationUploadService,
                         org.zanata.spring.service.CopyTransService copyTransService,
                         org.zanata.spring.service.MergeTransService mergeTransService,
                         org.zanata.spring.repository.ProjectRepository projectRepository,
                         org.zanata.spring.vaadin.ProgressDialogService progressDialogs,
                         org.zanata.spring.repository.AccountRepository accountRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
        this.sourceUploadService = sourceUploadService;
        this.translationUploadService = translationUploadService;
        this.copyTransService = copyTransService;
        this.mergeTransService = mergeTransService;
        this.projectRepository = projectRepository;
        this.progressDialogs = progressDialogs;
        this.accountRepository = accountRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private Long currentIterationId;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        String versionSlug = event.getRouteParameters().get("versionSlug").orElse("");
        this.currentProjectSlug = projectSlug;
        this.currentVersionSlug = versionSlug;

        HProjectIteration iteration = iterationRepository
                .findFullByProjectAndSlug(projectSlug, versionSlug)
                .orElseThrow(() -> new NotFoundException(
                        "Version not found: " + projectSlug + "/" + versionSlug));
        if (iteration.getProject() != null
                && iteration.getProject().getDefaultSourceLocale() != null
                && iteration.getProject().getDefaultSourceLocale().getLocaleId() != null) {
            this.currentSourceLocaleId =
                    iteration.getProject().getDefaultSourceLocale().getLocaleId().getId();
        }

        this.currentIterationId = iteration.getId();
        IterationStats stats = IterationStats.compute(iteration.getId(),
                iterationRepository, targetRepository, localeRepository);
        this.enabledLocales = stats.enabledLocales == null
                ? java.util.List.of() : stats.enabledLocales;
        // Cheap COUNT(*) for the Documents tab badge — was previously a full
        // findByVersion + .size(). That blew up on large versions (Consulo).
        long docCount = documentRepository.countByVersion(projectSlug, versionSlug);

        add(buildBreadcrumb(projectSlug));
        add(buildHeading(iteration));
        add(buildStatsRow(stats));
        ProgressBar bar = new ProgressBar(0.0, 1.0, stats.translatedPct / 100.0);
        bar.setWidthFull();
        bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
        add(bar);
        Paragraph total = new Paragraph(
                "Total source content: " + String.format("%,d", stats.totalSourceWords) + " words");
        total.addClassNames(LumoUtility.Margin.Vertical.SMALL);
        add(total);

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(tabWithBadge("Languages", stats.localeCount),
                buildLanguagesPanel(stats));
        tabs.add(tabWithBadge("Documents", (int) Math.min(Integer.MAX_VALUE, docCount)),
                buildDocumentsPanel());
        add(tabs);
    }

    private Breadcrumbs buildBreadcrumb(String projectSlug) {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(projectSlug, "/project/view/" + projectSlug, true)
        );
        return crumbs;
    }

    private HorizontalLayout buildHeading(HProjectIteration iteration) {
        H1 slug = new H1(iteration.getSlug());
        slug.addClassNames(LumoUtility.Margin.NONE);
        HorizontalLayout layout = new HorizontalLayout(slug);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        layout.setWidthFull();
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (iteration.getStatus() == EntityStatus.READONLY) {
            var lock = LineAwesomeIcon.LOCK_SOLID.create();
            lock.setSize("0.9em");
            lock.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            layout.addComponentAtIndex(1, lock);
        }
        // Settings link → /project/:slug/version/:vslug/settings
        com.vaadin.flow.component.button.Button settingsBtn =
                new com.vaadin.flow.component.button.Button("Settings",
                        LineAwesomeIcon.COG_SOLID.create(),
                        e -> UI.getCurrent().navigate(
                                "project/" + currentProjectSlug
                                + "/version/" + currentVersionSlug + "/settings"));
        settingsBtn.addThemeVariants(
                com.vaadin.flow.component.button.ButtonVariant.TERTIARY);

        // "More actions" menu — host for Copy Trans and (later) Merge Translations.
        com.vaadin.flow.component.menubar.MenuBar more =
                new com.vaadin.flow.component.menubar.MenuBar();
        more.addThemeVariants(
                com.vaadin.flow.component.menubar.MenuBarVariant.TERTIARY);
        var moreTrigger = more.addItem("More actions");
        moreTrigger.getElement().setAttribute("title", "Per-version operations");
        if (canUpload()) {
            moreTrigger.getSubMenu().addItem("Copy Translations…",
                    e -> openCopyTransDialog());
            moreTrigger.getSubMenu().addItem("Merge Translations…",
                    e -> openMergeTransDialog());
        }

        HorizontalLayout right = new HorizontalLayout(more, settingsBtn);
        right.setSpacing(true);
        layout.add(right);
        return layout;
    }

    /**
     * Open the Copy Trans dialog — three rule pickers + a Start button that
     * runs the operation inside the modal ProgressDialogService.
     */
    private void openCopyTransDialog() {
        com.vaadin.flow.component.dialog.Dialog dlg =
                new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Copy Translations");
        dlg.setWidth("min(640px, 92vw)");

        Paragraph intro = new Paragraph(
                "Find translations of identical source strings in other "
                + "documents on this server and copy them into this version.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("font-size", "0.9rem");

        com.vaadin.flow.component.combobox.ComboBox<org.zanata.spring.service.CopyTransService.Action>
                onProj = actionPicker("On project mismatch");
        com.vaadin.flow.component.combobox.ComboBox<org.zanata.spring.service.CopyTransService.Action>
                onDoc = actionPicker("On document mismatch");
        com.vaadin.flow.component.combobox.ComboBox<org.zanata.spring.service.CopyTransService.Action>
                onCtx = actionPicker("On context (resId) mismatch");

        com.vaadin.flow.component.formlayout.FormLayout form =
                new com.vaadin.flow.component.formlayout.FormLayout(onProj, onDoc, onCtx);
        form.setResponsiveSteps(
                new com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep("0", 1));

        com.vaadin.flow.component.button.Button cancel =
                new com.vaadin.flow.component.button.Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.TERTIARY);

        com.vaadin.flow.component.button.Button start =
                new com.vaadin.flow.component.button.Button("Copy Translations");
        start.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var opts = new org.zanata.spring.service.CopyTransService.Options(
                    onProj.getValue(), onDoc.getValue(), onCtx.getValue());
            dlg.close();
            Long iterId = currentIterationId;
            progressDialogs.run("Copy Trans — " + currentVersionSlug, handle -> {
                handle.update(0, 1, "Scanning untranslated text flows…");
                return copyTransService.runForIteration(iterId, opts, p ->
                        handle.update(p.processedFlows(), Math.max(1, p.totalFlows()),
                                p.processedFlows() + " / " + p.totalFlows()
                                + " — " + p.copied() + " copied"
                                + (p.copiedAsFuzzy() > 0
                                        ? ", " + p.copiedAsFuzzy() + " fuzzy" : "")));
            }).whenComplete((res, err) -> {
                if (err != null) {
                    com.vaadin.flow.component.notification.Notification.show(
                            "Copy Trans failed: " + err.getMessage(), 5000,
                            com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                    return;
                }
                com.vaadin.flow.component.notification.Notification.show(
                        "Copy Trans: " + res.copied() + " copied"
                        + (res.copiedAsFuzzy() > 0
                                ? ", " + res.copiedAsFuzzy() + " fuzzy" : "")
                        + " of " + res.totalFlows() + " untranslated",
                        4500,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            });
        });

        dlg.add(intro, form);
        dlg.getFooter().add(cancel, start);
        dlg.open();
    }

    private com.vaadin.flow.component.combobox.ComboBox<org.zanata.spring.service.CopyTransService.Action>
            actionPicker(String label) {
        var box = new com.vaadin.flow.component.combobox.ComboBox<org.zanata.spring.service.CopyTransService.Action>(label);
        box.setItems(org.zanata.spring.service.CopyTransService.Action.values());
        box.setValue(org.zanata.spring.service.CopyTransService.Action.Continue);
        box.setWidthFull();
        return box;
    }

    /**
     * Merge Translations dialog: pick a source project + source version, decide
     * whether to keep existing translations, then run the merge inside the
     * modal progress dialog.
     */
    private void openMergeTransDialog() {
        com.vaadin.flow.component.dialog.Dialog dlg =
                new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Merge Translations");
        dlg.setWidth("min(640px, 92vw)");

        Paragraph intro = new Paragraph(
                "Copy matching Translated / Approved translations from another "
                + "version into this one.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("font-size", "0.9rem");

        com.vaadin.flow.component.combobox.ComboBox<org.zanata.model.HProject> projectBox =
                new com.vaadin.flow.component.combobox.ComboBox<>("Source project");
        projectBox.setItemLabelGenerator(p -> p.getName() == null ? p.getSlug() : p.getName());
        projectBox.setWidthFull();
        projectBox.setItems(query -> projectRepository.search(
                        query.getFilter().orElse(""),
                        org.springframework.data.domain.PageRequest.of(
                                query.getPage(), query.getPageSize()))
                .stream());

        com.vaadin.flow.component.combobox.ComboBox<org.zanata.model.HProjectIteration> versionBox =
                new com.vaadin.flow.component.combobox.ComboBox<>("Source version");
        versionBox.setItemLabelGenerator(org.zanata.model.HProjectIteration::getSlug);
        versionBox.setWidthFull();
        versionBox.setEnabled(false);

        projectBox.addValueChangeListener(e -> {
            var proj = e.getValue();
            if (proj == null) {
                versionBox.setItems(java.util.List.of());
                versionBox.setEnabled(false);
                return;
            }
            // Reload with iterations attached so .getProjectIterations() works.
            var withIter = projectRepository.findBySlugWithIterations(proj.getSlug())
                    .orElse(proj);
            java.util.List<org.zanata.model.HProjectIteration> versions = withIter.getProjectIterations().stream()
                    .filter(i -> !i.getId().equals(currentIterationId))
                    .sorted(java.util.Comparator.comparing(
                            org.zanata.model.HProjectIteration::getSlug).reversed())
                    .toList();
            versionBox.setItems(versions);
            versionBox.setEnabled(!versions.isEmpty());
        });

        com.vaadin.flow.component.checkbox.Checkbox keepExisting =
                new com.vaadin.flow.component.checkbox.Checkbox(
                        "Keep existing translated / approved translations");
        keepExisting.setValue(true);
        keepExisting.setTooltipText(
                "When checked, only fills empty entries. When unchecked, "
                + "overwrites existing translations if the incoming one is newer.");

        com.vaadin.flow.component.formlayout.FormLayout form =
                new com.vaadin.flow.component.formlayout.FormLayout(projectBox, versionBox);
        form.setResponsiveSteps(
                new com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep("0", 1),
                new com.vaadin.flow.component.formlayout.FormLayout.ResponsiveStep("520px", 2));

        com.vaadin.flow.component.button.Button cancel =
                new com.vaadin.flow.component.button.Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.TERTIARY);

        com.vaadin.flow.component.button.Button start =
                new com.vaadin.flow.component.button.Button("Merge Translations");
        start.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var sourceIter = versionBox.getValue();
            if (sourceIter == null) {
                versionBox.setInvalid(true);
                versionBox.setErrorMessage("Pick a source version");
                return;
            }
            boolean keep = Boolean.TRUE.equals(keepExisting.getValue());
            Long targetIter = currentIterationId;
            Long srcId = sourceIter.getId();
            String srcSlug = sourceIter.getSlug();
            dlg.close();
            progressDialogs.run("Merge from " + srcSlug, handle -> {
                handle.update(0, 1, "Scanning…");
                return mergeTransService.runMerge(targetIter, srcId, keep, p ->
                        handle.update(p.processedFlows(), Math.max(1, p.totalFlows()),
                                p.processedFlows() + " / " + p.totalFlows()
                                + " — " + p.copied() + " copied"));
            }).whenComplete((res, err) -> {
                if (err != null) {
                    com.vaadin.flow.component.notification.Notification.show(
                            "Merge failed: " + err.getMessage(), 5000,
                            com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                    return;
                }
                com.vaadin.flow.component.notification.Notification.show(
                        "Merge: " + res.copied() + " of " + res.totalFlows()
                        + " entries copied from " + srcSlug,
                        4500,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            });
        });

        dlg.add(intro, form, keepExisting);
        dlg.getFooter().add(cancel, start);
        dlg.open();
    }

    private HorizontalLayout buildStatsRow(IterationStats stats) {
        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.add(statBlock(String.format("%.2f%%", stats.approvedPct), "approved",
                "var(--aura-green)"));
        row.add(statBlock(String.format("%.2f%%", stats.translatedPct), "translated",
                "var(--aura-green)"));
        row.add(statBlock(String.format("%.2f", stats.hoursRemaining),
                "total hours remaining", "var(--vaadin-text-color)"));
        return row;
    }

    private VerticalLayout statBlock(String value, String label, String color) {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setAlignItems(FlexComponent.Alignment.START);
        H2 valueEl = new H2(value);
        valueEl.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.XXLARGE);
        valueEl.getStyle().set("color", color);
        Span labelEl = new Span(label);
        labelEl.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        col.add(valueEl, labelEl);
        col.getStyle().set("padding-inline-end", "2rem");
        return col;
    }

    private Tab tabWithBadge(String label, int count) {
        Span badge = new Span(String.valueOf(count));
        badge.getElement().getThemeList().add("badge contrast small");
        badge.getStyle().set("margin-inline-start", "0.5rem");
        return new Tab(new Span(label), badge);
    }

    private Div buildLanguagesPanel(IterationStats stats) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");
        panel.getStyle().set("box-sizing", "border-box");
        panel.getStyle().set("overflow-x", "hidden");

        com.vaadin.flow.component.select.Select<String> sortSelect =
                new com.vaadin.flow.component.select.Select<>();
        sortSelect.setItems("Name", "Locale", "Translated %", "Approved %");
        sortSelect.setValue("Locale");
        sortSelect.setWidth("180px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3("Languages");
        title.addClassNames(LumoUtility.Margin.NONE);
        HorizontalLayout sortBox = new HorizontalLayout(
                new Span("Sort"), sortSelect);
        sortBox.setAlignItems(FlexComponent.Alignment.CENTER);
        sortBox.setSpacing(true);
        header.add(title, sortBox);
        panel.add(header);

        Span counter = new Span(stats.perLocale.size() + " languages");
        counter.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        counter.getStyle().set("display", "block");
        counter.getStyle().set("margin", "0.5rem 0 0.75rem 0");
        panel.add(counter);

        Div listContainer = new Div();
        listContainer.setWidthFull();
        renderLocaleList(listContainer, stats.perLocale, sortSelect.getValue());
        panel.add(listContainer);

        sortSelect.addValueChangeListener(e ->
                renderLocaleList(listContainer, stats.perLocale, e.getValue()));
        return panel;
    }

    private void renderLocaleList(Div container,
                                  List<IterationStats.LocaleStats> rows, String sortBy) {
        container.removeAll();
        java.util.Comparator<IterationStats.LocaleStats> cmp = switch (sortBy == null ? "" : sortBy) {
            case "Name" -> java.util.Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> {
                        String d = ls.locale.getDisplayName();
                        return d == null || d.isBlank()
                                ? (ls.locale.getLocaleId() == null ? ""
                                        : ls.locale.getLocaleId().getId())
                                : d;
                    },
                    String.CASE_INSENSITIVE_ORDER);
            case "Translated %" -> java.util.Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.translatedPct).reversed();
            case "Approved %" -> java.util.Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.totalSourceWords == 0 ? 0.0
                            : (double) ls.approvedWords / ls.totalSourceWords * 100.0).reversed();
            default -> java.util.Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> ls.locale.getLocaleId() == null
                            ? "" : ls.locale.getLocaleId().getId(),
                    String.CASE_INSENSITIVE_ORDER);
        };
        // Source locale always first, then everything else by the chosen sort.
        java.util.Comparator<IterationStats.LocaleStats> finalCmp =
                java.util.Comparator.<IterationStats.LocaleStats, Integer>comparing(
                        ls -> currentSourceLocaleId.equalsIgnoreCase(
                                ls.locale.getLocaleId() == null ? ""
                                        : ls.locale.getLocaleId().getId())
                                ? 0 : 1)
                        .thenComparing(cmp);
        rows.stream().sorted(finalCmp).forEach(ls -> container.add(buildLocaleRow(ls)));
    }

    private Div buildLocaleRow(IterationStats.LocaleStats ls) {
        Div card = new Div();
        card.addClassNames("zanata-locale-row",
                LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);
        card.getStyle().set("width", "100%");
        card.getStyle().set("box-sizing", "border-box");
        card.getStyle().set("cursor", "pointer");
        card.getStyle().set("padding", "0.85rem 1rem");
        card.getStyle().set("overflow", "hidden");
        card.getStyle().set("transition", "background-color 100ms ease-out");
        // Hover state — matches the legacy row interaction.
        card.getElement().executeJs(
                "this.addEventListener('mouseenter', () => "
                        + "this.style.backgroundColor='var(--vaadin-background-container)');"
                        + "this.addEventListener('mouseleave', () => "
                        + "this.style.backgroundColor='');");
        String localeIdStr = ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId();
        if (!localeIdStr.isBlank()
                && currentProjectSlug != null && currentVersionSlug != null) {
            card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(
                    "translate/" + currentProjectSlug + "/" + currentVersionSlug
                            + "/" + localeIdStr)));
        }

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout left = new VerticalLayout();
        left.setPadding(false);
        left.setSpacing(false);
        String display = ls.locale.getDisplayName();
        if (display == null || display.isBlank()) {
            display = ls.locale.getLocaleId() == null ? "" : ls.locale.getLocaleId().getId();
        }
        Span name = new Span(display);
        name.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        // Aura ships --aura-blue-text (light/dark aware); fall back to the
        // Lumo primary token if the theme ever changes.
        name.getStyle().set("color",
                "var(--aura-blue-text, var(--lumo-primary-text-color))");
        Span code = new Span(ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId());
        code.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        left.add(name, code);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        boolean isSource = currentSourceLocaleId.equalsIgnoreCase(localeIdStr);
        if (isSource) {
            Span src = new Span("Source");
            src.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
            src.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            src.getStyle().set("line-height", "1.1");
            Span tag = new Span("Primary contribution");
            tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
            right.add(src, tag);
        } else {
            Span pct = new Span(String.format("%.2f%%", ls.translatedPct));
            pct.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);
            pct.getStyle().set("color", "var(--aura-green)");
            pct.getStyle().set("line-height", "1.1");
            Span tag = new Span("Translated");
            tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
            right.add(pct, tag);
        }

        // Download / export actions tucked behind a single overflow (⋯) menu
        // so the row's width doesn't grow. The legacy UI did the same — one
        // dropdown in the top-right of the panel; here we put it per-row so
        // each locale's bundle is one click away. Hidden for the source
        // locale (en-US has nothing to download).
        com.vaadin.flow.component.menubar.MenuBar overflow = null;
        if (!localeIdStr.isBlank()
                && !currentSourceLocaleId.equalsIgnoreCase(localeIdStr)
                && currentProjectSlug != null && currentVersionSlug != null) {
            overflow = new com.vaadin.flow.component.menubar.MenuBar();
            overflow.addThemeVariants(
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY_INLINE,
                    com.vaadin.flow.component.menubar.MenuBarVariant.SMALL,
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_ICON);
            com.vaadin.flow.component.contextmenu.MenuItem trigger =
                    overflow.addItem(LineAwesomeIcon.ELLIPSIS_H_SOLID.create());
            trigger.getElement().setAttribute("aria-label", "Downloads");
            trigger.getElement().setAttribute("title", "Downloads");

            String offlineHref = "/rest/file/translation/"
                    + currentProjectSlug + "/" + currentVersionSlug + "/"
                    + localeIdStr + "/offlinepo";
            String tmxHref = "/rest/tm/projects/" + currentProjectSlug
                    + "/iterations/" + currentVersionSlug
                    + "?locale=" + localeIdStr;
            trigger.getSubMenu().addItem("Download All (zip)",
                    e -> UI.getCurrent().getPage().open(offlineHref, "_self"));
            trigger.getSubMenu().addItem("Export TMX",
                    e -> UI.getCurrent().getPage().open(tmxHref, "_self"));
            // Stop the card's click listener from firing when the menu is clicked.
            overflow.getElement().addEventListener("click", e -> {})
                    .addEventData("event.stopPropagation()");
        }

        HorizontalLayout rightCol = new HorizontalLayout(right);
        if (overflow != null) {
            rightCol.add(overflow);
        }
        rightCol.setAlignItems(FlexComponent.Alignment.CENTER);
        rightCol.setSpacing(true);
        rightCol.getStyle().set("flex-shrink", "0");

        left.getStyle().set("min-width", "0");
        left.getStyle().set("flex", "1 1 auto");

        row.add(left, rightCol);
        row.setFlexGrow(1, left);
        row.setFlexGrow(0, rightCol);
        card.add(row);

        if (!isSource) {
            ProgressBar bar = new ProgressBar(0.0, 1.0,
                    Math.max(0.0, Math.min(1.0, ls.translatedPct / 100.0)));
            bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
            bar.getStyle().set("margin-top", "0.4rem");
            card.add(bar);
        }
        return card;
    }

    private Div buildDocumentsPanel() {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");
        panel.getStyle().set("box-sizing", "border-box");

        if (canUpload()) {
            panel.add(buildUploader());
        }

        // Filter bar on top of the grid — server-side filter passed into the
        // DataProvider so the entire version's docs don't have to be loaded.
        com.vaadin.flow.component.textfield.TextField filter =
                new com.vaadin.flow.component.textfield.TextField();
        filter.setPlaceholder("Filter by docId or path…");
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        filter.getStyle().set("margin-bottom", "0.5rem");
        panel.add(filter);

        Grid<HDocument> grid = new Grid<>(HDocument.class, false);
        grid.addColumn(HDocument::getDocId).setHeader("Doc Id").setAutoWidth(true);
        grid.addColumn(d -> d.getPath() == null ? "" : d.getPath())
                .setHeader("Path").setAutoWidth(true);
        grid.addColumn(d -> d.getContentType() == null ? "" : d.getContentType().toString())
                .setHeader("Content type").setAutoWidth(true);
        grid.addColumn(d -> d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? "" : d.getLocale().getLocaleId().getId())
                .setHeader("Lang").setAutoWidth(true);
        // Per-doc actions: download translated file (any locale, anyone) +
        // upload translation (signed-in only). Packed into a single menu so
        // the row's width doesn't grow.
        if (!enabledLocales.isEmpty()) {
            java.util.List<org.zanata.model.HLocale> targetLocales = enabledLocales.stream()
                    .filter(l -> l.getLocaleId() != null
                            && !l.getLocaleId().getId().equalsIgnoreCase(currentSourceLocaleId))
                    .toList();
            boolean canUpload = canUpload();
            grid.addComponentColumn(doc -> {
                com.vaadin.flow.component.menubar.MenuBar mb =
                        new com.vaadin.flow.component.menubar.MenuBar();
                mb.addThemeVariants(
                        com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY_INLINE,
                        com.vaadin.flow.component.menubar.MenuBarVariant.SMALL,
                        com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_ICON);
                var trigger = mb.addItem(LineAwesomeIcon.ELLIPSIS_H_SOLID.create());
                trigger.getElement().setAttribute("aria-label", "Document actions");
                trigger.getElement().setAttribute("title", "Actions");
                if (!targetLocales.isEmpty()) {
                    var downloadItem = trigger.getSubMenu().addItem("Download translated…");
                    for (var loc : targetLocales) {
                        String code = loc.getLocaleId().getId();
                        String name = loc.getDisplayName() + " (" + code + ")";
                        String href = "/rest/file/translated-doc/"
                                + currentProjectSlug + "/" + currentVersionSlug
                                + "/" + code + "?docId="
                                + java.net.URLEncoder.encode(doc.getDocId(),
                                        java.nio.charset.StandardCharsets.UTF_8);
                        downloadItem.getSubMenu().addItem(name,
                                ev -> UI.getCurrent().getPage().open(href, "_self"));
                    }
                }
                if (canUpload) {
                    trigger.getSubMenu().addItem("Upload translation…",
                            e -> openTranslationUploadDialog(doc));
                }
                return mb;
            }).setHeader("Actions").setAutoWidth(true).setFlexGrow(0);
        }
        // Server-paged DataProvider — Vaadin pulls just the rows currently
        // in the viewport via the two callbacks below. Search filter is
        // applied at the SQL level.
        com.vaadin.flow.data.provider.CallbackDataProvider<HDocument, String> dp =
                com.vaadin.flow.data.provider.DataProvider.fromFilteringCallbacks(
                        query -> {
                            String q = query.getFilter().orElse("").trim().toLowerCase();
                            int page = query.getOffset() / Math.max(1, query.getLimit());
                            return documentRepository.pageByVersion(
                                    currentProjectSlug, currentVersionSlug, q,
                                    org.springframework.data.domain.PageRequest.of(page, query.getLimit()))
                                    .stream();
                        },
                        query -> {
                            String q = query.getFilter().orElse("").trim().toLowerCase();
                            long n = documentRepository.countMatchingByVersion(
                                    currentProjectSlug, currentVersionSlug, q);
                            return (int) Math.min(Integer.MAX_VALUE, n);
                        });
        com.vaadin.flow.data.provider.ConfigurableFilterDataProvider<HDocument, Void, String> filterable =
                dp.withConfigurableFilter();
        filterable.setFilter("");
        grid.setDataProvider(filterable);
        filter.addValueChangeListener(e -> filterable.setFilter(
                e.getValue() == null ? "" : e.getValue()));

        grid.setHeight("70vh");
        panel.add(grid);
        return panel;
    }

    /**
     * Open a small modal that lets the user pick a target locale and upload
     * a translation file for a single document. Mirrors the legacy "Upload
     * Translations" flow (Project → Version → Document → ⋮ → Upload).
     */
    private void openTranslationUploadDialog(HDocument doc) {
        var dlg = new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Upload translation — " + doc.getDocId());
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(false);

        // Filter out the source locale — uploading a translation for the
        // source language is meaningless.
        java.util.List<org.zanata.model.HLocale> targets = enabledLocales.stream()
                .filter(l -> l.getLocaleId() != null
                        && !l.getLocaleId().getId().equalsIgnoreCase(currentSourceLocaleId))
                .toList();

        com.vaadin.flow.component.combobox.ComboBox<org.zanata.model.HLocale> localeBox =
                new com.vaadin.flow.component.combobox.ComboBox<>("Target language");
        localeBox.setItems(targets);
        localeBox.setItemLabelGenerator(l -> l.getDisplayName()
                + " (" + l.getLocaleId().getId() + ")");
        localeBox.setWidthFull();
        localeBox.setRequired(true);
        if (targets.size() == 1) localeBox.setValue(targets.get(0));

        var buffer = new com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer();
        var upload = new com.vaadin.flow.component.upload.Upload(buffer);
        upload.setAcceptedFileTypes(".properties", ".po", ".pot",
                ".xlf", ".xliff", ".yaml", ".yml");
        upload.setDropLabel(new Span("Drop the translation file here, or click to choose"));
        upload.setAutoUpload(false); // wait for the user to hit Upload below
        upload.setMaxFiles(1);

        Paragraph intro = new Paragraph(
                "Translations are matched to text-flows by their resId. "
                + "Existing translations for matched keys are snapshotted to "
                + "history before being overwritten.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("font-size", "0.9rem");
        intro.getStyle().set("margin", "0 0 0.5rem 0");

        com.vaadin.flow.component.button.Button cancel =
                new com.vaadin.flow.component.button.Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.TERTIARY);

        com.vaadin.flow.component.button.Button start =
                new com.vaadin.flow.component.button.Button("Upload");
        start.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var locale = localeBox.getValue();
            if (locale == null || locale.getLocaleId() == null) {
                localeBox.setInvalid(true);
                localeBox.setErrorMessage("Pick a target language first");
                return;
            }
            java.util.Set<String> files = buffer.getFiles();
            if (files.isEmpty()) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Choose a file to upload first", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }
            String fileName = files.iterator().next();
            var actor = currentAccount();
            if (actor == null) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Sign in to upload translations", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }
            try (java.io.InputStream in = buffer.getInputStream(fileName)) {
                var result = translationUploadService.upload(
                        currentProjectSlug, currentVersionSlug, doc.getDocId(),
                        fileName, locale.getLocaleId().getId(), in, actor);
                com.vaadin.flow.component.notification.Notification.show(
                        "Imported " + result.accepted() + " translations"
                                + (result.skipped() > 0 ? " (skipped " + result.skipped() + ")" : ""),
                        3500,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
                dlg.close();
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Upload failed: " + ex.getMessage(), 5000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
            }
        });

        dlg.add(intro, localeBox, upload);
        dlg.getFooter().add(cancel, start);
        dlg.open();
    }

    private com.vaadin.flow.component.html.Div buildUploader() {
        var buffer = new com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer();
        var upload = new com.vaadin.flow.component.upload.Upload(buffer);
        upload.setAcceptedFileTypes(".properties", ".po", ".pot", ".xlf", ".xliff");
        upload.setDropLabel(new com.vaadin.flow.component.html.Span(
                "Drop a .properties / .po / .xlf file here, or click to upload"));
        upload.addSucceededListener(e -> {
            String filename = e.getFileName();
            var actor = currentAccount();
            if (actor == null) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Sign in to upload documents", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }
            try (java.io.InputStream in = buffer.getInputStream(filename)) {
                var result = sourceUploadService.upload(
                        currentProjectSlug, currentVersionSlug, filename, in, actor);
                com.vaadin.flow.component.notification.Notification.show(
                        (result.created() ? "Created" : "Updated") + " "
                                + result.document().getDocId(),
                        2500,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Upload failed: " + ex.getMessage(), 5000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
            }
        });
        com.vaadin.flow.component.html.Div wrap = new com.vaadin.flow.component.html.Div(upload);
        wrap.getStyle().set("margin-bottom", "0.75rem");
        return wrap;
    }

    private boolean canUpload() {
        return currentAccount() != null;
    }

    private org.zanata.model.HAccount currentAccount() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        return accountRepository.findByUsername(auth.getName()).orElse(null);
    }
}
