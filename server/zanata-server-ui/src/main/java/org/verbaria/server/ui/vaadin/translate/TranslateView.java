package org.verbaria.server.ui.vaadin.translate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.verbaria.server.headless.service.TranslationEditService;
import org.verbaria.server.headless.service.ai.TranslationProvider;
import org.verbaria.server.headless.service.ai.TranslationProviderRegistry;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.component.virtuallist.VirtualList;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;
import com.vaadin.flow.data.provider.Query;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import java.util.HashMap;
import java.util.Map;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.ObjectProvider;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
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

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.MessageEvaluateType;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import org.verbaria.server.ui.vaadin.service.EditorPreferencesService;
import org.verbaria.server.headless.service.ai.AiPolicyService;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.ExploreView;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.ui.vaadin.ProgressDialogService;
import org.verbaria.server.ui.vaadin.iteration.IterationView;
import org.verbaria.server.ui.vaadin.project.ProjectView;

@Route(value = "translate/:projectSlug/:versionSlug/:localeId", layout = MainLayout.class)
@AnonymousAllowed
public class TranslateView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.translate"; }



    private static final Set<ContentState> COMPLETE = EnumSet.of(
            ContentState.Translated, ContentState.Approved);

    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TranslationEditService translationEditService;
    private final BreadcrumbsService breadcrumbsService;
    private final ObjectProvider<TranslationRow> rowFactory;

    private LocaleId currentLocale;
    private LocaleId sourceLocale;
    private String projectSlug;
    private String versionSlug;
    private String localeStr;
    private String currentDocId;

    private final TextField filter = new TextField();
    // Labels set lazily in beforeEnter() so getTranslation() resolves with
    // the right UI locale (field initializers run before MainLayout applies
    // the cookie-stored locale).
    private final Checkbox incompleteOnly = new Checkbox();
    private final Checkbox completeOnly   = new Checkbox();
    /** Virtual list driven by a server-paged CallbackDataProvider. */
    private final VirtualList<RowData> rowsList = new VirtualList<>();

    /**
     * Snapshot of everything the row renderer needs. Equality/hashCode are
     * derived only from {@code flow.id} so Vaadin's {@code KeyMapper} never
     * walks lazy associations on {@code HTextFlowTarget} (e.g. its translator
     * {@code HPerson}) outside a session.
     */
    private static final class RowData {
        final HTextFlow flow;
        final Optional<HTextFlowTarget> existing;
        final ContentState state;
        final String source;
        RowData(HTextFlow flow, Optional<HTextFlowTarget> existing,
                ContentState state, String source) {
            this.flow = flow;
            this.existing = existing;
            this.state = state;
            this.source = source;
        }
        HTextFlow flow() { return flow; }
        Optional<HTextFlowTarget> existing() { return existing; }
        ContentState state() { return state; }
        String source() { return source; }
        @Override public int hashCode() {
            return flow == null || flow.getId() == null ? 0 : flow.getId().hashCode();
        }
        @Override public boolean equals(Object o) {
            return o instanceof RowData r
                    && Objects.equals(
                            flow == null ? null : flow.getId(),
                            r.flow == null ? null : r.flow.getId());
        }
    }

    public TranslateView(DocumentRepository documentRepository,
                         TextFlowRepository textFlowRepository,
                         TextFlowTargetRepository targetRepository,
                         TranslationEditService translationEditService,
                         TranslationProviderRegistry aiRegistry,
                         AiPolicyService aiPolicy,
                         ProgressDialogService progressDialogs,
                         EditorPreferencesService editorPreferences,
                         BreadcrumbsService breadcrumbsService,
                         ProjectRepository projectRepository,
                         ObjectProvider<TranslationRow> rowFactory) {
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.translationEditService = translationEditService;
        this.aiRegistry = aiRegistry;
        this.aiPolicy = aiPolicy;
        this.progressDialogs = progressDialogs;
        this.editorPreferences = editorPreferences;
        this.breadcrumbsService = breadcrumbsService;
        this.projectRepository = projectRepository;
        this.rowFactory = rowFactory;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private final ProjectRepository projectRepository;
    /** Project-level message-evaluate setting, resolved per navigation. */
    private MessageEvaluateType messageEvaluateType = MessageEvaluateType.NONE;

    private final TranslationProviderRegistry aiRegistry;
    private final AiPolicyService aiPolicy;
    private final ProgressDialogService progressDialogs;
    private final EditorPreferencesService editorPreferences;
    private EditorPreferencesService.Prefs prefs = EditorPreferencesService.Prefs.DEFAULTS;

    /**
     * Per-row history panel state, keyed by {@code flow.id}. The VirtualList
     * tears down and rebuilds rows as they scroll out of / into the viewport,
     * which would otherwise wipe each panel's visibility back to the
     * {@code prefs.autoOpenHistory()} default. We persist the explicit
     * open/closed state here so user toggles stick across renders.
     */
    private final Set<Long> historyExpanded =
            ConcurrentHashMap.newKeySet();
    private final Set<Long> historyCollapsed =
            ConcurrentHashMap.newKeySet();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        prefs = editorPreferences.load();
        projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        versionSlug = event.getRouteParameters().get("versionSlug").orElse("");
        localeStr   = event.getRouteParameters().get("localeId").orElse("");
        if (projectSlug.isBlank() || versionSlug.isBlank() || localeStr.isBlank()) {
            throw new NotFoundException(getTranslation("translate.error.missingRouteParams"));
        }
        currentLocale = new LocaleId(localeStr);

        // Prefer an explicit ?doc= query param, fall back to the legacy
        // "messages" docId for the demo about-fedora seed, then fall back to
        // the first non-obsolete doc in the iteration. The proper fix is to
        // add :docId to the route — pending until the editor supports
        // switching docs in-place.
        String docHint = event.getLocation().getQueryParameters()
                .getSingleParameter("doc").orElse(null);
        HDocument doc = null;
        if (docHint != null && !docHint.isBlank()) {
            doc = documentRepository
                    .findByVersionAndDocId(projectSlug, versionSlug, docHint)
                    .orElse(null);
        }
        if (doc == null) {
            doc = documentRepository
                    .findByVersionAndDocId(projectSlug, versionSlug, "messages")
                    .orElse(null);
        }
        if (doc == null) {
            List<HDocument> docs = documentRepository.findByVersion(projectSlug, versionSlug);
            if (!docs.isEmpty()) {
                doc = docs.get(0);
            }
        }
        if (doc == null) {
            showEmptyState();
            return;
        }
        currentDocId = doc.getDocId();

        // Project-level message-evaluate setting (Java/ICU4J/printf) drives the
        // editor's "Evaluate" preview. Read by slug so we don't touch lazy
        // associations outside a transaction.
        var project = projectRepository.findBySlug(projectSlug).orElse(null);
        messageEvaluateType = project == null
                ? MessageEvaluateType.NONE : project.getMessageEvaluateType();

        // No more eager findByDocument — DataProvider pulls pages from DB.
        sourceLocale = doc.getLocale() != null && doc.getLocale().getLocaleId() != null
                ? doc.getLocale().getLocaleId() : LocaleId.EN_US;
        Long docIdForProvider = doc.getId();

        publishBreadcrumb();

        HorizontalLayout headingRow = new HorizontalLayout();
        headingRow.setWidthFull();
        headingRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headingRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H2 heading = new H2(currentDocId + " \u2192 " + localeStr);
        heading.addClassNames(AuraUtility.Margin.NONE);

        // Doc switcher — renders immediately as just a button. The expensive
        // findByVersion + per-doc count queries are deferred until the user
        // clicks it, and surfaced via ProgressDialogService so the wait has
        // visible feedback instead of a silent inline "Loading…".
        boolean viewingSource = sourceLocale != null
                && sourceLocale.getId().equalsIgnoreCase(localeStr);
        Long iterId = doc.getProjectIteration() == null
                ? null : doc.getProjectIteration().getId();
        Button docSwitcher = new Button(currentDocId + "  \u25BE");
        docSwitcher.addThemeVariants(ButtonVariant.TERTIARY);
        docSwitcher.addClassNames(AuraUtility.FontWeight.SEMIBOLD);
        docSwitcher.getStyle().set("max-width", "520px");
        docSwitcher.addClassNames(AuraUtility.Overflow.HIDDEN, AuraUtility.TextOverflow.ELLIPSIS);
        Popover docPopover =
                new Popover();
        docPopover.setPosition(PopoverPosition.BOTTOM);
        docPopover.setWidth("760px");
        docPopover.setHeight("70vh");
        docPopover.setOpenOnClick(false); // we trigger manually after the load
        docPopover.setModal(false);
        docPopover.setTarget(docSwitcher);
        add(docPopover);
        docSwitcher.addClickListener(e ->
                openDocPickerVia(docPopover, docSwitcher, iterId, viewingSource));

        // Stats popover — same pattern. buildStatsBadge() runs findByDocument
        // over the entire version (slow on large versions like Consulo) so
        // it gets the same visible progress treatment.
        Button statsBtn = new Button(getTranslation("translate.stats"));
        statsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        statsBtn.setIcon(LineAwesomeIcon.CHART_BAR_SOLID.create());
        Popover statsPopover =
                new Popover();
        statsPopover.setTarget(statsBtn);
        statsPopover.setWidth("420px");
        statsPopover.setOpenOnClick(false);
        statsBtn.addClickListener(e -> openStatsVia(statsPopover, statsBtn));

        HorizontalLayout headingRight = new HorizontalLayout();
        headingRight.setAlignItems(FlexComponent.Alignment.CENTER);
        headingRight.setSpacing(true);
        if (docSwitcher != null) {
            headingRight.add(docSwitcher);
        }
        boolean atSourceLocale = sourceLocale != null
                && sourceLocale.getId().equalsIgnoreCase(localeStr);
        if (!atSourceLocale && aiPolicy.canCurrentUserUseAi()) {
            headingRight.add(buildBulkAiButton(doc));
        }
        headingRight.add(statsBtn);

        Button prefsBtn = new Button(LineAwesomeIcon.COG_SOLID.create(),
                e -> openEditorSettings());
        prefsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        prefsBtn.getElement().setAttribute("title", getTranslation("translate.editorSettings"));
        prefsBtn.getElement().setAttribute("aria-label", getTranslation("translate.editorSettings"));
        headingRight.add(prefsBtn);

        headingRow.add(heading, headingRight);
        add(headingRow, statsPopover);

        add(buildFilterBar());

        // VirtualList renders only the rows currently in the viewport.
        // For documents with hundreds of text-flows (e.g. Consulo
        // *Localize.yaml files often have 300+) eager DOM construction
        // pegged the browser; the virtual list keeps it responsive.
        rowsList.setWidthFull();
        rowsList.setHeight("calc(100vh - 220px)");  // fills below the filter bar
        rowsList.getStyle().set("max-width", "100%");
        rowsList.setRenderer(new ComponentRenderer<Component, RowData>(d ->
                rowFactory.getObject().populate(rowContext(), d.flow(),
                        d.existing(), d.state(), d.source())));
        add(rowsList);

        installDataProvider(docIdForProvider);

        // Alt+Y → keyboard-shortcuts help. Bound at the view level so it
        // works no matter which row (if any) has focus.
        Shortcuts.addShortcutListener(this, this::openShortcutsHelp,
                Key.KEY_Y, KeyModifier.ALT);
    }

    /**
     * Editor settings popover — per-user preferences stored on the account
     * via {@link EditorPreferencesService}. Anonymous users see the same UI
     * but changes don't persist.
     */
    private void openEditorSettings() {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("editor.settings.title"));
        dlg.setWidth("420px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(true);

        Button closeX = new Button(LineAwesomeIcon.TIMES_SOLID.create(), e -> dlg.close());
        closeX.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        closeX.getElement().setAttribute("aria-label", getTranslation("common.close"));
        closeX.getElement().setAttribute("title", getTranslation("common.close"));
        dlg.getHeader().add(closeX);

        Checkbox compact = new Checkbox(getTranslation("editor.settings.compact"));
        compact.setValue(prefs.compactRows());
        compact.setHelperText(getTranslation("editor.settings.compactHint"));

        Checkbox showReview = new Checkbox(getTranslation("editor.settings.showReview"));
        showReview.setValue(prefs.showReviewComments());
        showReview.setHelperText(getTranslation("editor.settings.showReviewHint"));

        Checkbox autoHistory = new Checkbox(getTranslation("editor.settings.autoOpenHistory"));
        autoHistory.setValue(prefs.autoOpenHistory());

        boolean signedIn = isAuthenticated();
        if (!signedIn) {
            Paragraph note = new Paragraph(getTranslation("editor.settings.signInToPersist"));
            note.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL, AuraUtility.Margin.Bottom.SMALL);
            dlg.add(note);
        }

        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        Button save = new Button(getTranslation("common.save"), e -> {
            var next = new EditorPreferencesService.Prefs(
                    Boolean.TRUE.equals(compact.getValue()),
                    Boolean.TRUE.equals(showReview.getValue()),
                    Boolean.TRUE.equals(autoHistory.getValue()));
            editorPreferences.save(next);
            prefs = next;
            if (rowsList.getDataProvider() != null) {
                rowsList.getDataProvider().refreshAll();
            }
            Notification.show(getTranslation(signedIn
                            ? "editor.settings.savedSignedIn"
                            : "editor.settings.savedAnonymous"),
                    2000, Notification.Position.BOTTOM_END);
            dlg.close();
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        dlg.add(compact, showReview, autoHistory);
        dlg.getFooter().add(cancel, save);
        dlg.open();
    }

    /** Modal cheat-sheet of the editor's keyboard shortcuts. */
    private void openShortcutsHelp() {
        Dialog dlg =
                new Dialog();
        dlg.setHeaderTitle(getTranslation("translate.shortcuts.dialogTitle"));
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);

        String html = "<table style=\"border-collapse:collapse;width:100%;font-size:0.9rem\">"
                + row(getTranslation("translate.shortcuts.shortcutAltY"),
                        getTranslation("translate.shortcuts.descShowHelp"))
                + row(getTranslation("translate.shortcuts.shortcutEsc"),
                        getTranslation("translate.shortcuts.descCloseAny"))
                + row(getTranslation("translate.shortcuts.shortcutAltG"),
                        getTranslation("translate.shortcuts.descCopySource"))
                + row(getTranslation("translate.shortcuts.shortcutAltX"),
                        getTranslation("translate.shortcuts.descClearTranslation"))
                + row(getTranslation("translate.shortcuts.shortcutCtrlEnter"),
                        getTranslation("translate.shortcuts.descSaveTranslated"))
                + row(getTranslation("translate.shortcuts.shortcutCtrlS"),
                        getTranslation("translate.shortcuts.descSaveFuzzy"))
                + "</table>";
        Div content = new Div();
        content.getElement().setProperty("innerHTML", html);
        dlg.add(content);

        Button close = new Button(getTranslation("progress.close"), e -> dlg.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        dlg.getFooter().add(close);
        dlg.open();
    }

    private static String row(String key, String desc) {
        return "<tr><td style=\"padding:0.3rem 0.5rem;font-family:monospace;"
                + "color:var(--aura-blue-text);white-space:nowrap\">" + key
                + "</td><td style=\"padding:0.3rem 0.5rem\">" + desc + "</td></tr>";
    }

    private MenuBar buildBulkAiButton(HDocument doc) {
        MenuBar mb = new MenuBar();
        mb.addThemeVariants(
                MenuBarVariant.TERTIARY,
                MenuBarVariant.SMALL);
        var item = mb.addItem(getTranslation("translate.action.aiBulk"));
        item.getElement().setAttribute("title", getTranslation("translate.action.aiTooltip"));
        for (var provider : aiRegistry.available()) {
            item.getSubMenu().addItem(provider.displayName(), e -> runBulkAi(doc, provider));
        }
        return mb;
    }

    private void runBulkAi(HDocument doc, TranslationProvider provider) {
        List<HTextFlow> flows = textFlowRepository.pageForTranslateView(
                doc.getId(), currentLocale, "", 1,
                PageRequest.of(0, 1000));
        if (flows.isEmpty()) {
            Notification.show(getTranslation("translate.bulkAi.nothing"),
                    2000, Notification.Position.BOTTOM_START);
            return;
        }
        List<TranslationProvider.TranslationRequest> reqs =
                new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) {
            String src = tf.getContents().isEmpty() ? "" : tf.getContents().get(0);
            String ctx = tf.getPotEntryData() == null ? null : tf.getPotEntryData().getContext();
            reqs.add(new TranslationProvider.TranslationRequest(
                    src, sourceLocale, currentLocale, ctx));
        }

        int total = reqs.size();
        String providerName = provider.displayName();
        String title = getTranslation("ai.translate.bulkRunning", providerName);
        progressDialogs.run(title, handle -> {
            handle.update(0, total, handle.t("ai.translate.bulkProgress", 0, total));
            List<String> out = provider.translateBatch(reqs,
                    (done, t) -> handle.update(done, t,
                            handle.t("ai.translate.bulkProgress", done, t)));
            handle.update(total, total, handle.t("ai.translate.bulkSaving"));
            int saved = 0;
            for (int i = 0; i < flows.size(); i++) {
                String translated = out.get(i);
                if (translated == null || translated.isBlank()) continue;
                try {
                    translationEditService.save(flows.get(i).getId(), currentLocale, translated);
                    saved++;
                } catch (Exception ignore) {
                }
            }
            return saved;
        }).whenComplete((saved, err) -> {
            if (err != null) {
                Notification.show(getTranslation("translate.bulkAi.failed", err.getMessage()),
                        5000, Notification.Position.MIDDLE);
                return;
            }
            Notification.show(getTranslation("translate.bulkAi.result", saved, total),
                    4000, Notification.Position.BOTTOM_START);
            rowsList.getDataProvider().refreshAll();
        });
    }

    /**
     * Triggered by clicking the doc-switcher button. Runs the expensive
     * findByVersion + per-doc count queries inside a ProgressDialogService
     * modal (visible status + progress bar), then populates the popover
     * with the rendered grid and opens it.
     */
    private void openDocPickerVia(Popover pop,
                                  Button trigger, Long iterId, boolean viewingSource) {
        trigger.setEnabled(false);
        String capturedVersionSlug = versionSlug;
        String capturedLocaleStr = localeStr;
        progressDialogs.run(getTranslation("translate.docSwitcher.title"), handle -> {
            handle.update(0, 3, handle.t("translate.docSwitcher.listing", capturedVersionSlug));
            List<HDocument> allDocs = documentRepository
                    .findByVersion(projectSlug, versionSlug);
            handle.update(1, 3, handle.t("translate.docSwitcher.countingTextFlows"));
            Map<Long, Long> totalByDoc = new HashMap<>();
            Map<Long, Long> translatedByDoc = new HashMap<>();
            if (iterId != null) {
                for (Object[] row : textFlowRepository.countPerDocForIteration(iterId)) {
                    totalByDoc.put(((Number) row[0]).longValue(),
                            ((Number) row[1]).longValue());
                }
                handle.update(2, 3, handle.t("translate.docSwitcher.countingTranslated", capturedLocaleStr));
                for (Object[] row : targetRepository
                        .translatedCountPerDocForLocale(iterId, currentLocale)) {
                    translatedByDoc.put(((Number) row[0]).longValue(),
                            ((Number) row[1]).longValue());
                }
            }
            handle.update(3, 3, handle.t("translate.docSwitcher.rendering"));
            return new Object[] { allDocs, totalByDoc, translatedByDoc };
        }).whenComplete((data, err) -> {
            trigger.setEnabled(true);
            if (err != null) {
                Notification.show(getTranslation("translate.action.history.empty", err.getMessage()),
                        5000, Notification.Position.MIDDLE);
                return;
            }
            @SuppressWarnings("unchecked")
            List<HDocument> allDocs = (List<HDocument>) data[0];
            @SuppressWarnings("unchecked")
            Map<Long, Long> totalByDoc = (Map<Long, Long>) data[1];
            @SuppressWarnings("unchecked")
            Map<Long, Long> translatedByDoc = (Map<Long, Long>) data[2];
            VerticalLayout body = buildDocPickerBody(
                    allDocs, totalByDoc, translatedByDoc, viewingSource, pop);
            pop.removeAll();
            pop.add(body);
            pop.open();
        });
    }

    /**
     * Triggered by clicking the Stats button. Same pattern as the doc picker:
     * heavy aggregation runs in the ProgressDialogService modal, then the
     * built badge is dropped into the popover which then opens.
     */
    private void openStatsVia(Popover pop, Button trigger) {
        trigger.setEnabled(false);
        String capturedDocId = currentDocId;
        // Capture translations on the UI thread; the worker thread can't call getTranslation().
        StatLabels labels = new StatLabels(
                getTranslation("translate.stats.row.approved"),
                getTranslation("translate.stats.row.translated"),
                getTranslation("translate.stats.row.needsReview"),
                getTranslation("translate.stats.row.untranslated"));
        progressDialogs.run(getTranslation("translate.stats.computing"), handle -> {
            handle.status(handle.t("translate.stats.aggregating", capturedDocId));
            return buildStatsBadge(labels, handle);
        }).whenComplete((badge, err) -> {
            trigger.setEnabled(true);
            if (err != null) {
                Notification.show(getTranslation("common.failed", err.getMessage()),
                        5000, Notification.Position.MIDDLE);
                return;
            }
            pop.removeAll();
            pop.add(badge);
            pop.open();
        });
    }

    /** Body for the doc picker — formerly the bulk of buildDocPickerPopover. */
    private VerticalLayout buildDocPickerBody(
            List<HDocument> allDocs,
            Map<Long, Long> totalByDoc,
            Map<Long, Long> translatedByDoc,
            boolean viewingSource,
            Popover pop) {
        TextField search = new TextField();
        search.setPlaceholder(getTranslation("translate.docSwitcher.searchPlaceholder"));
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(150);

        Grid<HDocument> grid =
                new Grid<>();
        grid.setSizeFull();
        grid.setSelectionMode(Grid.SelectionMode.NONE);

        grid.addColumn(HDocument::getDocId)
                .setHeader(getTranslation("translate.docPicker.column.document"))
                .setFlexGrow(1)
                .setSortable(true)
                .setKey("docId");

        ToIntFunction<HDocument> pctOf = d -> {
            long total = totalByDoc.getOrDefault(d.getId(), 0L);
            long done = viewingSource ? total
                    : translatedByDoc.getOrDefault(d.getId(), 0L);
            return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        };
        grid.addComponentColumn(d -> {
            long total = totalByDoc.getOrDefault(d.getId(), 0L);
            long done = viewingSource ? total
                    : translatedByDoc.getOrDefault(d.getId(), 0L);
            int pct = pctOf.applyAsInt(d);
            String colorClass = pct == 100 ? AuraUtility.TextColor.SUCCESS
                    : pct >= 50 ? AuraUtility.TextColor.ORANGE
                    : AuraUtility.TextColor.ERROR;
            Span pill = new Span(String.format("%d%% (%d/%d)", pct, done, total));
            pill.addClassNames(colorClass, AuraUtility.FontWeight.SEMIBOLD);
            pill.getStyle().set("font-variant-numeric", "tabular-nums");
            return pill;
        }).setHeader(getTranslation("translate.docPicker.column.translated"))
                .setWidth("180px").setFlexGrow(0)
                .setSortable(true).setComparator(Comparator.comparingInt(pctOf));

        ListDataProvider<HDocument> dp =
                new ListDataProvider<>(allDocs);
        grid.setDataProvider(dp);
        search.addValueChangeListener(e -> {
            String q = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
            dp.setFilter(d -> q.isEmpty() || d.getDocId().toLowerCase().contains(q));
        });

        grid.addItemClickListener(ev -> {
            HDocument picked = ev.getItem();
            if (picked == null) return;
            LoggerFactory.getLogger(TranslateView.class)
                    .info("doc-picker → switch to {}", picked.getDocId());
            // Close client-side first so the popover disappears instantly,
            // independent of the server roundtrip for navigate().
            pop.getElement().executeJs("this.opened = false;");
            pop.close();
            if (picked.getDocId().equals(currentDocId)) return;
            UI.getCurrent().navigate(
                    "translate/" + projectSlug + "/" + versionSlug + "/" + localeStr,
                    com.vaadin.flow.router.QueryParameters.simple(
                            Map.of("doc", picked.getDocId())));
        });

        // Search stays pinned at top, grid takes the rest and scrolls internally.
        // The Popover content slot doesn't propagate its height down to flex
        // children, so we give the body an explicit viewport-relative height
        // matching the popover's 70vh.
        search.addClassNames(AuraUtility.Flex.SHRINK_NONE);
        grid.setSizeFull();
        grid.addClassNames(AuraUtility.MinHeight.NONE, AuraUtility.Flex.ONE);
        grid.setAllRowsVisible(false);

        VerticalLayout body = new VerticalLayout(search, grid);
        body.setPadding(true);
        body.setSpacing(true);
        body.setWidthFull();
        body.setHeight("calc(70vh - 2rem)");
        body.setFlexGrow(0, search);
        body.setFlexGrow(1, grid);
        body.addClassNames(AuraUtility.MinHeight.NONE, AuraUtility.Overflow.HIDDEN);
        return body;
    }

    private Div buildFilterBar() {
        filter.setPlaceholder(getTranslation("translate.filter.placeholder"));
        incompleteOnly.setLabel(getTranslation("translate.filter.incomplete"));
        incompleteOnly.getElement().setAttribute("title",
                getTranslation("translate.filter.incompleteTip"));
        completeOnly.setLabel(getTranslation("translate.filter.complete"));
        completeOnly.getElement().setAttribute("title",
                getTranslation("translate.filter.completeTip"));
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        // Filter/checkbox listeners are installed by installDataProvider(),
        // which has access to the per-doc provider instance.
        // Stop the label wrapping when the bar runs out of room.
        incompleteOnly.addClassNames(AuraUtility.Whitespace.NOWRAP);
        completeOnly.addClassNames(AuraUtility.Whitespace.NOWRAP);

        HorizontalLayout checks = new HorizontalLayout(incompleteOnly, completeOnly);
        checks.setAlignItems(FlexComponent.Alignment.CENTER);
        checks.setSpacing(true);
        checks.addClassNames(AuraUtility.Flex.SHRINK_NONE, AuraUtility.Whitespace.NOWRAP);

        HorizontalLayout bar = new HorizontalLayout(filter, checks);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setSpacing(true);
        bar.setFlexGrow(1, filter);

        Div wrap = new Div(bar);
        wrap.addClassNames(AuraUtility.Border.BOTTOM, AuraUtility.BorderColor.DEFAULT, AuraUtility.Padding.Top.SMALL, AuraUtility.Padding.Bottom.MEDIUM);
        wrap.setWidthFull();
        return wrap;
    }

    /** Snapshot of UI filter state passed to the DataProvider. */
    private record FilterSpec(String q, int stateMode) {}

    private FilterSpec currentFilterSpec() {
        String q = filter.getValue() == null ? "" : filter.getValue().trim().toLowerCase();
        int mode = incompleteOnly.getValue() ? 1
                : (completeOnly.getValue() ? 2 : 0);
        return new FilterSpec(q, mode);
    }

    /** Wire VirtualList to a server-paged DataProvider — never loads the whole doc into memory. */
    private void installDataProvider(Long docId) {
        CallbackDataProvider<RowData, FilterSpec> provider =
                DataProvider.fromFilteringCallbacks(
                        query -> fetchPage(docId, query),
                        query -> countMatching(docId, query));
        ConfigurableFilterDataProvider<RowData, Void, FilterSpec>
                filterable = provider.withConfigurableFilter();
        filterable.setFilter(currentFilterSpec());
        rowsList.setDataProvider(filterable);
        // refresh provider when filter inputs change
        filter.addValueChangeListener(e -> filterable.setFilter(currentFilterSpec()));
        incompleteOnly.addValueChangeListener(e -> {
            if (Boolean.TRUE.equals(incompleteOnly.getValue())) completeOnly.setValue(false);
            filterable.setFilter(currentFilterSpec());
        });
        completeOnly.addValueChangeListener(e -> {
            if (Boolean.TRUE.equals(completeOnly.getValue())) incompleteOnly.setValue(false);
            filterable.setFilter(currentFilterSpec());
        });
    }

    private Stream<RowData> fetchPage(Long docId,
            Query<RowData, FilterSpec> query) {
        FilterSpec f = query.getFilter().orElse(new FilterSpec("", 0));
        int page = query.getOffset() / Math.max(1, query.getLimit());
        PageRequest pr =
                PageRequest.of(page, query.getLimit());
        List<HTextFlow> flows = textFlowRepository.pageForTranslateView(
                docId, currentLocale, f.q(), f.stateMode(), pr);
        if (flows.isEmpty()) return Stream.empty();
        List<Long> ids = new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) ids.add(tf.getId());
        Map<Long, HTextFlowTarget> tByFlow = new HashMap<>();
        for (HTextFlowTarget t : targetRepository.findByTextFlowIdsAndLocale(ids, currentLocale)) {
            tByFlow.put(t.getTextFlow().getId(), t);
        }
        return flows.stream().map(tf -> {
            HTextFlowTarget tgt = tByFlow.get(tf.getId());
            Optional<HTextFlowTarget> opt = Optional.ofNullable(tgt);
            ContentState state = opt.map(HTextFlowTarget::getState).orElse(ContentState.New);
            String src = tf.getContents().isEmpty() ? "" : tf.getContents().get(0);
            return new RowData(tf, opt, state, src);
        });
    }

    private int countMatching(Long docId,
            Query<RowData, FilterSpec> query) {
        FilterSpec f = query.getFilter().orElse(new FilterSpec("", 0));
        long n = textFlowRepository.countForTranslateView(
                docId, currentLocale, f.q(), f.stateMode());
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    /**
     * Render a friendly "nothing to translate yet" view for a version/locale
     * that has no documents, rather than failing navigation. The breadcrumb
     * still works so the user can go back up.
     */
    private void showEmptyState() {
        currentDocId = null;
        publishBreadcrumb();
        H2 heading = new H2(projectSlug + "/" + versionSlug + " \u2192 " + localeStr);
        heading.addClassNames(AuraUtility.Margin.NONE);
        Paragraph message = new Paragraph(  getTranslation("translate.empty.noDocs", projectSlug, versionSlug));
        message.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Top.MEDIUM);
        add(heading, message);
    }

    private void publishBreadcrumb() {
        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.projects"), "/explore"),
                BreadcrumbsService.Crumb.of(projectSlug, "/project/view/" + projectSlug),
                BreadcrumbsService.Crumb.of(versionSlug,
                        "/project/" + projectSlug + "/version/" + versionSlug),
                BreadcrumbsService.Crumb.here(localeStr)
        );
    }


    /** Pre-translated stat labels — captured on the UI thread then passed
     *  into the background worker that builds the stats badge. */
    private record StatLabels(String approved, String translated,
                              String needsReview, String untranslated) {}

    private Div buildStatsBadge(StatLabels labels,
                                ProgressDialogService.Handle handle) {
        // Stats now computed via two bulk queries (no per-row N+1) using
        // the current docId — looked up at the time the popover is built.
        HDocument doc = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, currentDocId)
                .orElse(null);
        long total = 0, translated = 0, approved = 0, needsReview = 0;
        long totalW = 0, translatedW = 0, approvedW = 0, needsReviewW = 0;
        if (doc != null) {
            for (HTextFlow tf : textFlowRepository.findByDocument(doc.getId())) {
                long wc = tf.getWordCount() == null ? 0 : tf.getWordCount();
                total++; totalW += wc;
            }
            for (Object[] row : targetRepository.countByDocAndLocale(doc.getId(), currentLocale)) {
                ContentState s = (ContentState) row[0];
                long n = ((Number) row[1]).longValue();
                switch (s) {
                    case Translated -> translated += n;
                    case Approved   -> approved += n;
                    case NeedReview, Rejected -> needsReview += n;
                    default -> {}
                }
            }
            // Word counts per state need a separate aggregate; reuse the
            // iteration-level aggregator and filter by this doc's textflows.
            // Approximation: assume word distribution mirrors message count.
            if (total > 0) {
                translatedW = totalW * translated / total;
                approvedW   = totalW * approved   / total;
                needsReviewW = totalW * needsReview / total;
            }
        }
        long untranslated = total - translated - approved - needsReview;
        long untranslatedW = totalW - translatedW - approvedW - needsReviewW;
        double pct = totalW == 0 ? 0.0 : (translatedW + approvedW) * 100.0 / totalW;

        Div badge = new Div();
        badge.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.Vertical.SMALL, AuraUtility.Padding.Horizontal.MEDIUM, AuraUtility.Background.BASE, AuraUtility.FontSize.SMALL);
        badge.getStyle().set("min-width", "420px");

        Div top = new Div(new Span(handle.t("translate.stats.translatedSummary",
                String.format("%.1f", pct),
                translatedW + approvedW, totalW,
                translated + approved, total)));
        top.addClassNames(AuraUtility.FontWeight.SEMIBOLD, AuraUtility.Margin.Bottom.SMALL);
        badge.add(top);

        badge.add(statRow(labels.approved(),     approvedW,     approved));
        badge.add(statRow(labels.translated(),   translatedW,   translated));
        badge.add(statRow(labels.needsReview(), needsReviewW,  needsReview));
        badge.add(statRow(labels.untranslated(), untranslatedW, untranslated));
        return badge;
    }

    private Div statRow(String label, long words, long msgs) {
        Div row = new Div();
        row.addClassNames(AuraUtility.Display.FLEX, AuraUtility.Gap.MEDIUM, AuraUtility.JustifyContent.BETWEEN);
        Span l = new Span(label);
        l.addClassNames(AuraUtility.TextColor.SECONDARY);
        Span w = new Span(words + "w");
        Span m = new Span(msgs + "m");
        row.add(l, w, m);
        return row;
    }

    private TranslationRow.RowContext rowContext() {
        return new TranslationRow.RowContext(
                prefs, currentLocale, sourceLocale, localeStr,
                projectSlug, versionSlug, currentDocId, messageEvaluateType,
                historyExpanded, historyCollapsed, this::refreshRows);
    }

    /**
     * Re-fetch the visible rows from the database so a row reflects the new
     * persisted state after a save/approve/reject (the renderer rebuilds each
     * row from a fresh RowData snapshot). Without this the action buttons keep
     * the state they were rendered with until a full page reload.
     */
    private void refreshRows() {
        if (rowsList.getDataProvider() != null) {
            rowsList.getDataProvider().refreshAll();
        }
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}
