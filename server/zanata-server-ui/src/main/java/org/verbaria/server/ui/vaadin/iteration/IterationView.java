package org.verbaria.server.ui.vaadin.iteration;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.contextmenu.MenuItem;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.service.CopyTransService;
import org.verbaria.server.headless.service.MergeTransService;
import org.verbaria.server.headless.service.SourceUploadService;
import org.verbaria.server.headless.service.TranslationUploadService;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.ExploreView;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.ui.vaadin.ProgressDialogService;
import org.verbaria.server.ui.vaadin.project.ProjectView;
import org.verbaria.server.ui.vaadin.stats.IterationStats;

@Route(value = "project/:projectSlug/version/:versionSlug", layout = MainLayout.class)
@RouteAlias(value = "iteration/view/:projectSlug/:versionSlug", layout = MainLayout.class)
@AnonymousAllowed
public class IterationView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.projectVersion"; }


    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;

    private String currentProjectSlug;
    private String currentVersionSlug;
    private String currentSourceLocaleId = "en-US";

    private final SourceUploadService sourceUploadService;
    private final TranslationUploadService translationUploadService;
    private final CopyTransService copyTransService;
    private final MergeTransService mergeTransService;
    private final ProjectRepository projectRepository;
    private final ProgressDialogService progressDialogs;
    private final AccountRepository accountRepository;
    private final BreadcrumbsService breadcrumbsService;

    /** Enabled locales for the current iteration — populated in beforeEnter for the upload dialog. */
    private List<HLocale> enabledLocales = List.of();

    public IterationView(ProjectIterationRepository iterationRepository,
                         DocumentRepository documentRepository,
                         TextFlowTargetRepository targetRepository,
                         LocaleRepository localeRepository,
                         SourceUploadService sourceUploadService,
                         TranslationUploadService translationUploadService,
                         CopyTransService copyTransService,
                         MergeTransService mergeTransService,
                         ProjectRepository projectRepository,
                         ProgressDialogService progressDialogs,
                         AccountRepository accountRepository,
                         BreadcrumbsService breadcrumbsService) {
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
        this.breadcrumbsService = breadcrumbsService;
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
                ? List.of() : stats.enabledLocales;
        // Cheap COUNT(*) for the Documents tab badge — was previously a full
        // findByVersion + .size(). That blew up on large versions (Consulo).
        long docCount = documentRepository.countByVersion(projectSlug, versionSlug);

        publishBreadcrumb(projectSlug, versionSlug);
        add(buildHeading(iteration));
        add(buildStatsRow(stats));
        ProgressBar bar = new ProgressBar(0.0, 1.0, stats.translatedPct / 100.0);
        bar.setWidthFull();
        bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
        add(bar);
        Paragraph total = new Paragraph(
                getTranslation("iteration.totalSourceWords", String.format("%,d", stats.totalSourceWords)));
        total.addClassNames(AuraUtility.Margin.Vertical.SMALL);
        add(total);

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(tabWithBadge(getTranslation("iteration.tab.languages"), stats.localeCount),
                buildLanguagesPanel(stats));
        tabs.add(tabWithBadge(getTranslation("iteration.tab.documents"), (int) Math.min(Integer.MAX_VALUE, docCount)),
                buildDocumentsPanel());
        add(tabs);
    }

    private void publishBreadcrumb(String projectSlug, String versionSlug) {
        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/explore"),
                // Project slug links back to the project overview so the user
                // can hop sideways between versions; the current version slug
                // is the "here" leaf.
                BreadcrumbsService.Crumb.of(projectSlug, "/project/view/" + projectSlug),
                BreadcrumbsService.Crumb.here(versionSlug)
        );
    }

    private HorizontalLayout buildHeading(HProjectIteration iteration) {
        H1 slug = new H1(iteration.getSlug());
        slug.addClassNames(AuraUtility.Margin.NONE);
        HorizontalLayout layout = new HorizontalLayout(slug);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        layout.setWidthFull();
        layout.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        if (iteration.getStatus() == EntityStatus.READONLY) {
            var lock = LineAwesomeIcon.LOCK_SOLID.create();
            lock.setSize("0.9em");
            lock.addClassNames(AuraUtility.TextColor.SECONDARY);
            layout.addComponentAtIndex(1, lock);
        }
        // Settings link → /project/:slug/version/:vslug/settings
        Button settingsBtn =
                new Button(getTranslation("iteration.heading.settings"),
                        LineAwesomeIcon.COG_SOLID.create(),
                        e -> UI.getCurrent().navigate(
                                "project/" + currentProjectSlug
                                + "/version/" + currentVersionSlug + "/settings"));
        settingsBtn.addThemeVariants(
                ButtonVariant.TERTIARY);

        // "More actions" menu — host for Copy Trans and (later) Merge Translations.
        MenuBar more =
                new MenuBar();
        more.addThemeVariants(
                MenuBarVariant.TERTIARY);
        var moreTrigger = more.addItem(getTranslation("iteration.heading.more"));
        moreTrigger.getElement().setAttribute("title",
                getTranslation("iteration.heading.moreTip"));
        if (canUpload()) {
            moreTrigger.getSubMenu().addItem(
                    getTranslation("iteration.heading.copyTransMenu"),
                    e -> openCopyTransDialog());
            moreTrigger.getSubMenu().addItem(
                    getTranslation("iteration.heading.mergeTransMenu"),
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
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("iteration.copyTrans.title"));
        dlg.setWidth("min(640px, 92vw)");

        Paragraph intro = new Paragraph(getTranslation("iteration.copyTrans.intro"));
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);

        ComboBox<CopyTransService.Action> onProj =
                actionPicker(getTranslation("iteration.copyTrans.onProject"));
        ComboBox<CopyTransService.Action> onDoc =
                actionPicker(getTranslation("iteration.copyTrans.onDoc"));
        ComboBox<CopyTransService.Action> onCtx =
                actionPicker(getTranslation("iteration.copyTrans.onContext"));

        FormLayout form = new FormLayout(onProj, onDoc, onCtx);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);

        Button start = new Button(getTranslation("iteration.copyTrans.start"));
        start.addThemeVariants(ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var opts = new CopyTransService.Options(
                    onProj.getValue(), onDoc.getValue(), onCtx.getValue());
            dlg.close();
            Long iterId = currentIterationId;
            String modalTitle = getTranslation("iteration.copyTrans.modalTitle", currentVersionSlug);
            progressDialogs.run(modalTitle, handle -> {
                // Background-safe i18n: use handle.t(...) instead of getTranslation()
                handle.update(0, 1, handle.t("iteration.copyTrans.scanning"));
                return copyTransService.runForIteration(iterId, opts, p -> {
                    String status = p.copiedAsFuzzy() > 0
                            ? handle.t("iteration.copyTrans.progressWithFuzzy",
                                    p.processedFlows(), p.totalFlows(),
                                    p.copied(), p.copiedAsFuzzy())
                            : handle.t("iteration.copyTrans.progress",
                                    p.processedFlows(), p.totalFlows(), p.copied());
                    handle.update(p.processedFlows(), Math.max(1, p.totalFlows()), status);
                });
            }).whenComplete((res, err) -> {
                if (err != null) {
                    Notification.show(getTranslation("iteration.copyTrans.failed", err.getMessage()),
                            5000, Notification.Position.MIDDLE);
                    return;
                }
                String done = res.copiedAsFuzzy() > 0
                        ? getTranslation("iteration.copyTrans.resultWithFuzzy",
                                res.copied(), res.copiedAsFuzzy(), res.totalFlows())
                        : getTranslation("iteration.copyTrans.result",
                                res.copied(), res.totalFlows());
                Notification.show(done, 4500, Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            });
        });

        dlg.add(intro, form);
        dlg.getFooter().add(cancel, start);
        dlg.open();
    }

    private ComboBox<CopyTransService.Action> actionPicker(String label) {
        var box = new ComboBox<CopyTransService.Action>(label);
        box.setItems(CopyTransService.Action.values());
        box.setValue(CopyTransService.Action.Continue);
        box.setWidthFull();
        return box;
    }

    /**
     * Merge Translations dialog: pick a source project + source version, decide
     * whether to keep existing translations, then run the merge inside the
     * modal progress dialog.
     */
    private void openMergeTransDialog() {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("iteration.merge.title"));
        dlg.setWidth("min(640px, 92vw)");

        Paragraph intro = new Paragraph(getTranslation("iteration.merge.intro"));
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);

        ComboBox<org.zanata.model.HProject> projectBox =
                new ComboBox<>(getTranslation("iteration.merge.sourceProject"));
        projectBox.setItemLabelGenerator(p -> p.getName() == null ? p.getSlug() : p.getName());
        projectBox.setWidthFull();
        projectBox.setItems(query -> projectRepository.search(
                        query.getFilter().orElse(""),
                        PageRequest.of(query.getPage(), query.getPageSize()))
                .stream());

        ComboBox<org.zanata.model.HProjectIteration> versionBox =
                new ComboBox<>(getTranslation("iteration.merge.sourceVersion"));
        versionBox.setItemLabelGenerator(org.zanata.model.HProjectIteration::getSlug);
        versionBox.setWidthFull();
        versionBox.setEnabled(false);

        projectBox.addValueChangeListener(e -> {
            var proj = e.getValue();
            if (proj == null) {
                versionBox.setItems(List.of());
                versionBox.setEnabled(false);
                return;
            }
            var withIter = projectRepository.findBySlugWithIterations(proj.getSlug())
                    .orElse(proj);
            List<org.zanata.model.HProjectIteration> versions = withIter.getProjectIterations().stream()
                    .filter(i -> !i.getId().equals(currentIterationId))
                    .sorted(Comparator.comparing(
                            org.zanata.model.HProjectIteration::getSlug).reversed())
                    .toList();
            versionBox.setItems(versions);
            versionBox.setEnabled(!versions.isEmpty());
        });

        Checkbox keepExisting = new Checkbox(getTranslation("iteration.merge.keepExisting"));
        keepExisting.setValue(true);
        keepExisting.setTooltipText(getTranslation("iteration.merge.keepExistingTip"));

        FormLayout form = new FormLayout(projectBox, versionBox);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("520px", 2));

        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);

        Button start = new Button(getTranslation("iteration.merge.start"));
        start.addThemeVariants(ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var sourceIter = versionBox.getValue();
            if (sourceIter == null) {
                versionBox.setInvalid(true);
                versionBox.setErrorMessage(getTranslation("iteration.merge.pickVersion"));
                return;
            }
            boolean keep = Boolean.TRUE.equals(keepExisting.getValue());
            Long targetIter = currentIterationId;
            Long srcId = sourceIter.getId();
            String srcSlug = sourceIter.getSlug();
            dlg.close();
            String modalTitle = getTranslation("iteration.merge.modalTitle", srcSlug);
            progressDialogs.run(modalTitle, handle -> {
                handle.update(0, 1, handle.t("iteration.merge.scanning"));
                return mergeTransService.runMerge(targetIter, srcId, keep, p ->
                        handle.update(p.processedFlows(), Math.max(1, p.totalFlows()),
                                handle.t("iteration.merge.progress",
                                        p.processedFlows(), p.totalFlows(), p.copied())));
            }).whenComplete((res, err) -> {
                if (err != null) {
                    Notification.show(getTranslation("iteration.merge.failed", err.getMessage()),
                            5000, Notification.Position.MIDDLE);
                    return;
                }
                Notification.show(getTranslation("iteration.merge.result",
                                res.copied(), res.totalFlows(), srcSlug),
                        4500, Notification.Position.BOTTOM_START);
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
        row.add(statBlock(String.format("%.2f%%", stats.approvedPct),
                getTranslation("iteration.stats.approved"),
                AuraUtility.TextColor.SUCCESS));
        row.add(statBlock(String.format("%.2f%%", stats.translatedPct),
                getTranslation("iteration.stats.translated"),
                AuraUtility.TextColor.SUCCESS));
        row.add(statBlock(String.format("%.2f", stats.hoursRemaining),
                getTranslation("iteration.stats.hoursRemaining"),
                AuraUtility.TextColor.BODY));
        return row;
    }

    private VerticalLayout statBlock(String value, String label, String valueColorClass) {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setAlignItems(FlexComponent.Alignment.START);
        H2 valueEl = new H2(value);
        valueEl.addClassNames(AuraUtility.Margin.NONE, AuraUtility.FontSize.XXLARGE,
                valueColorClass);
        Span labelEl = new Span(label);
        labelEl.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY);
        col.add(valueEl, labelEl);
        col.addClassNames(AuraUtility.Padding.End.LARGE);
        return col;
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

    private Div buildLanguagesPanel(IterationStats stats) {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM, AuraUtility.Width.FULL, AuraUtility.BoxSizing.BORDER, AuraUtility.Overflow.X.HIDDEN);

        Select<String> sortSelect =
                new Select<>();
        String sortName = getTranslation("iteration.languages.sortName");
        String sortLocale = getTranslation("iteration.languages.sortLocale");
        String sortTranslated = getTranslation("iteration.languages.sortTranslated");
        String sortApproved = getTranslation("iteration.languages.sortApproved");
        sortSelect.setItems(sortName, sortLocale, sortTranslated, sortApproved);
        sortSelect.setValue(sortLocale);
        sortSelect.setWidth("180px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3(getTranslation("iteration.languages.heading"));
        title.addClassNames(AuraUtility.Margin.NONE);
        HorizontalLayout sortBox = new HorizontalLayout(
                new Span(getTranslation("iteration.languages.sort")), sortSelect);
        sortBox.setAlignItems(FlexComponent.Alignment.CENTER);
        sortBox.setSpacing(true);
        header.add(title, sortBox);
        panel.add(header);

        Span counter = new Span(getTranslation("iteration.languages.counter",
                stats.perLocale.size()));
        counter.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY,
                AuraUtility.Display.BLOCK,
                AuraUtility.Margin.Top.SMALL, AuraUtility.Margin.Bottom.MEDIUM);
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
        // Match against translated sort labels (locale-dependent) — can't use
        // switch with constant string cases here.
        String sortName = getTranslation("iteration.languages.sortName");
        String sortTranslated = getTranslation("iteration.languages.sortTranslated");
        String sortApproved = getTranslation("iteration.languages.sortApproved");
        String key = sortBy == null ? "" : sortBy;
        Comparator<IterationStats.LocaleStats> cmp;
        if (key.equals(sortName)) {
            cmp = Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> {
                        String d = ls.locale.getDisplayName();
                        return d == null || d.isBlank()
                                ? (ls.locale.getLocaleId() == null ? ""
                                        : ls.locale.getLocaleId().getId())
                                : d;
                    },
                    String.CASE_INSENSITIVE_ORDER);
        } else if (key.equals(sortTranslated)) {
            cmp = Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.translatedPct).reversed();
        } else if (key.equals(sortApproved)) {
            cmp = Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.totalSourceWords == 0 ? 0.0
                            : (double) ls.approvedWords / ls.totalSourceWords * 100.0).reversed();
        } else {
            cmp = Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> ls.locale.getLocaleId() == null
                            ? "" : ls.locale.getLocaleId().getId(),
                    String.CASE_INSENSITIVE_ORDER);
        }
        // Source locale always first, then everything else by the chosen sort.
        Comparator<IterationStats.LocaleStats> finalCmp =
                Comparator.<IterationStats.LocaleStats, Integer>comparing(
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
                AuraUtility.Border.BOTTOM, AuraUtility.BorderColor.SECONDARY, AuraUtility.Width.FULL, AuraUtility.BoxSizing.BORDER, AuraUtility.Cursor.POINTER, AuraUtility.Padding.MEDIUM, AuraUtility.Overflow.HIDDEN);
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
        name.addClassNames(AuraUtility.FontSize.LARGE, AuraUtility.FontWeight.BOLD);
        // Aura ships --aura-blue-text (light/dark aware); fall back to the
        // Lumo primary token if the theme ever changes.
        name.getStyle().set("color",
                "var(--aura-blue-text)");
        Span code = new Span(ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId());
        code.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY);
        left.add(name, code);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        boolean isSource = currentSourceLocaleId.equalsIgnoreCase(localeIdStr);
        if (isSource) {
            Span src = new Span(getTranslation("iteration.languages.source"));
            src.addClassNames(AuraUtility.FontSize.LARGE, AuraUtility.FontWeight.BOLD, AuraUtility.TextColor.SECONDARY, AuraUtility.LineHeight.XSMALL);
            Span tag = new Span(getTranslation("iteration.languages.primaryContribution"));
            tag.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.TextColor.SECONDARY);
            right.add(src, tag);
        } else {
            Span pct = new Span(String.format("%.2f%%", ls.translatedPct));
            pct.addClassNames(AuraUtility.FontSize.XLARGE, AuraUtility.FontWeight.BOLD,
                    AuraUtility.TextColor.SUCCESS, AuraUtility.LineHeight.XSMALL);
            Span tag = new Span(getTranslation("iteration.languages.translated"));
            tag.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.TextColor.SECONDARY);
            right.add(pct, tag);
        }

        // Download / export actions tucked behind a single overflow (⋯) menu
        // so the row's width doesn't grow. The legacy UI did the same — one
        // dropdown in the top-right of the panel; here we put it per-row so
        // each locale's bundle is one click away. Hidden for the source
        // locale (en-US has nothing to download).
        MenuBar overflow = null;
        if (!localeIdStr.isBlank()
                && !currentSourceLocaleId.equalsIgnoreCase(localeIdStr)
                && currentProjectSlug != null && currentVersionSlug != null) {
            overflow = new MenuBar();
            overflow.addThemeVariants(MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
            MenuItem trigger =
                    overflow.addItem(LineAwesomeIcon.ELLIPSIS_H_SOLID.create());
            trigger.getElement().setAttribute("aria-label",
                    getTranslation("common.download"));
            trigger.getElement().setAttribute("title",
                    getTranslation("common.download"));

            String offlineHref = "/rest/file/translation/"
                    + currentProjectSlug + "/" + currentVersionSlug + "/"
                    + localeIdStr + "/offlinepo";
            String tmxHref = "/rest/tm/projects/" + currentProjectSlug
                    + "/iterations/" + currentVersionSlug
                    + "?locale=" + localeIdStr;
            trigger.getSubMenu().addItem(getTranslation("iteration.menu.downloadAll"),
                    e -> UI.getCurrent().getPage().open(offlineHref, "_self"));
            trigger.getSubMenu().addItem(getTranslation("iteration.menu.exportTmx"),
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
        rightCol.addClassNames(AuraUtility.Flex.SHRINK_NONE);

        left.addClassNames(AuraUtility.MinWidth.NONE, AuraUtility.Flex.AUTO);

        row.add(left, rightCol);
        row.setFlexGrow(1, left);
        row.setFlexGrow(0, rightCol);
        card.add(row);

        if (!isSource) {
            ProgressBar bar = new ProgressBar(0.0, 1.0,
                    Math.max(0.0, Math.min(1.0, ls.translatedPct / 100.0)));
            bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
            bar.addClassNames(AuraUtility.Margin.Top.SMALL);
            card.add(bar);
        }
        return card;
    }

    private Div buildDocumentsPanel() {
        Div panel = new Div();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM, AuraUtility.Width.FULL, AuraUtility.BoxSizing.BORDER);

        if (canUpload()) {
            panel.add(buildUploader());
        }

        // Filter bar on top of the grid — server-side filter passed into the
        // DataProvider so the entire version's docs don't have to be loaded.
        TextField filter =
                new TextField();
        filter.setPlaceholder(getTranslation("iteration.docs.filterPlaceholder"));
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        filter.addClassNames(AuraUtility.Margin.Bottom.SMALL);
        panel.add(filter);

        Grid<HDocument> grid = new Grid<>(HDocument.class, false);
        grid.addColumn(HDocument::getDocId)
                .setHeader(getTranslation("iteration.docs.colDocId")).setAutoWidth(true);
        grid.addColumn(d -> d.getPath() == null ? "" : d.getPath())
                .setHeader(getTranslation("iteration.docs.colPath")).setAutoWidth(true);
        grid.addColumn(d -> d.getContentType() == null ? "" : d.getContentType().toString())
                .setHeader(getTranslation("iteration.docs.colContentType")).setAutoWidth(true);
        grid.addColumn(d -> d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? "" : d.getLocale().getLocaleId().getId())
                .setHeader(getTranslation("iteration.docs.colLang")).setAutoWidth(true);
        // Per-doc actions: download translated file (any locale, anyone) +
        // upload translation (signed-in only). Packed into a single menu so
        // the row's width doesn't grow.
        if (!enabledLocales.isEmpty()) {
            List<HLocale> targetLocales = enabledLocales.stream()
                    .filter(l -> l.getLocaleId() != null
                            && !l.getLocaleId().getId().equalsIgnoreCase(currentSourceLocaleId))
                    .toList();
            boolean canUpload = canUpload();
            grid.addComponentColumn(doc -> {
                MenuBar mb = new MenuBar();
                mb.addThemeVariants( MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
                var trigger = mb.addItem(LineAwesomeIcon.ELLIPSIS_H_SOLID.create());
                trigger.getElement().setAttribute("aria-label",
                        getTranslation("iteration.docs.actionsAria"));
                trigger.getElement().setAttribute("title",
                        getTranslation("iteration.docs.actionsTip"));
                if (!targetLocales.isEmpty()) {
                    var downloadItem = trigger.getSubMenu().addItem(
                            getTranslation("iteration.actions.downloadTranslated"));
                    for (var loc : targetLocales) {
                        String code = loc.getLocaleId().getId();
                        String name = loc.getDisplayName() + " (" + code + ")";
                        String href = "/rest/file/translated-doc/"
                                + currentProjectSlug + "/" + currentVersionSlug
                                + "/" + code + "?docId="
                                + URLEncoder.encode(doc.getDocId(),
                                        StandardCharsets.UTF_8);
                        downloadItem.getSubMenu().addItem(name,
                                ev -> UI.getCurrent().getPage().open(href, "_self"));
                    }
                }
                if (canUpload) {
                    trigger.getSubMenu().addItem(
                            getTranslation("iteration.actions.uploadTranslation"),
                            e -> openTranslationUploadDialog(doc));
                }
                return mb;
            }).setHeader(getTranslation("iteration.docs.colActions"))
                    .setAutoWidth(true).setFlexGrow(0);
        }
        // Server-paged DataProvider — Vaadin pulls just the rows currently
        // in the viewport via the two callbacks below. Search filter is
        // applied at the SQL level.
        CallbackDataProvider<HDocument, String> dp =
                DataProvider.fromFilteringCallbacks(
                        query -> {
                            String q = query.getFilter().orElse("").trim().toLowerCase();
                            int page = query.getOffset() / Math.max(1, query.getLimit());
                            return documentRepository.pageByVersion(
                                    currentProjectSlug, currentVersionSlug, q,
                                    PageRequest.of(page, query.getLimit()))
                                    .stream();
                        },
                        query -> {
                            String q = query.getFilter().orElse("").trim().toLowerCase();
                            long n = documentRepository.countMatchingByVersion(
                                    currentProjectSlug, currentVersionSlug, q);
                            return (int) Math.min(Integer.MAX_VALUE, n);
                        });
        ConfigurableFilterDataProvider<HDocument, Void, String> filterable =
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
        var dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("iteration.upload.title", doc.getDocId()));
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(false);

        // Filter out the source locale — uploading a translation for the
        // source language is meaningless.
        List<HLocale> targets = enabledLocales.stream()
                .filter(l -> l.getLocaleId() != null
                        && !l.getLocaleId().getId().equalsIgnoreCase(currentSourceLocaleId))
                .toList();

        ComboBox<HLocale> localeBox =
                new ComboBox<>(getTranslation("iteration.upload.targetLanguage"));
        localeBox.setItems(targets);
        localeBox.setItemLabelGenerator(l -> l.getDisplayName()
                + " (" + l.getLocaleId().getId() + ")");
        localeBox.setWidthFull();
        localeBox.setRequired(true);
        if (targets.size() == 1) localeBox.setValue(targets.get(0));

        var buffer = new MultiFileMemoryBuffer();
        var upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".properties", ".po", ".pot",
                ".xlf", ".xliff", ".yaml", ".yml");
        upload.setDropLabel(new Span(getTranslation("iteration.upload.dropLabel")));
        upload.setAutoUpload(false); // wait for the user to hit Upload below
        upload.setMaxFiles(1);

        Paragraph intro = new Paragraph(getTranslation("iteration.upload.intro"));
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL, AuraUtility.Margin.Bottom.SMALL);

        Button cancel =
                new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);

        Button start =
                new Button(getTranslation("common.upload"));
        start.addThemeVariants(ButtonVariant.PRIMARY);
        start.addClickListener(e -> {
            var locale = localeBox.getValue();
            if (locale == null || locale.getLocaleId() == null) {
                localeBox.setInvalid(true);
                localeBox.setErrorMessage(getTranslation("iteration.upload.pickLanguage"));
                return;
            }
            Set<String> files = buffer.getFiles();
            if (files.isEmpty()) {
                Notification.show(
                        getTranslation("iteration.upload.pickFile"), 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            String fileName = files.iterator().next();
            var actor = currentAccount();
            if (actor == null) {
                Notification.show(
                        getTranslation("iteration.upload.signInRequired"), 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            try (InputStream in = buffer.getInputStream(fileName)) {
                var result = translationUploadService.upload(
                        currentProjectSlug, currentVersionSlug, doc.getDocId(),
                        fileName, locale.getLocaleId().getId(), in, actor);
                String msg = result.skipped() > 0
                        ? getTranslation("iteration.upload.successWithSkipped",
                                result.accepted(), result.skipped())
                        : getTranslation("iteration.upload.success", result.accepted());
                Notification.show(msg, 3500,
                        Notification.Position.BOTTOM_START);
                dlg.close();
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show(
                        getTranslation("iteration.upload.failed", ex.getMessage()), 5000,
                        Notification.Position.MIDDLE);
            }
        });

        dlg.add(intro, localeBox, upload);
        dlg.getFooter().add(cancel, start);
        dlg.open();
    }

    private Div buildUploader() {
        var buffer = new MultiFileMemoryBuffer();
        var upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".properties", ".po", ".pot", ".xlf", ".xliff");
        upload.setDropLabel(new Span(getTranslation("iteration.source.dropLabel")));
        upload.addSucceededListener(e -> {
            String filename = e.getFileName();
            var actor = currentAccount();
            if (actor == null) {
                Notification.show(
                        getTranslation("iteration.source.signInRequired"), 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            try (InputStream in = buffer.getInputStream(filename)) {
                var result = sourceUploadService.upload(
                        currentProjectSlug, currentVersionSlug, filename, in, actor);
                String docId = result.document().getDocId();
                Notification.show(
                        getTranslation(result.created() ? "iteration.source.created"
                                : "iteration.source.updated", docId),
                        2500,
                        Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                Notification.show(
                        getTranslation("iteration.upload.failed", ex.getMessage()), 5000,
                        Notification.Position.MIDDLE);
            }
        });
        Div wrap = new Div(upload);
        wrap.addClassNames(AuraUtility.Margin.Bottom.MEDIUM);
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
