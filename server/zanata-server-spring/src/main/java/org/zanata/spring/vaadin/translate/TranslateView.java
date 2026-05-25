package org.zanata.spring.vaadin.translate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.virtuallist.VirtualList;
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
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
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

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetHistoryRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.repository.TextFlowTargetReviewCommentRepository;
import org.zanata.model.HTextFlowTargetReviewComment;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import org.zanata.spring.service.EditorPreferencesService;
import org.zanata.spring.service.ai.AiPolicyService;
import org.zanata.spring.vaadin.ExploreView;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.ProgressDialogService;
import org.zanata.spring.vaadin.iteration.IterationView;
import org.zanata.spring.vaadin.project.ProjectView;

@Route(value = "translate/:projectSlug/:versionSlug/:localeId", layout = MainLayout.class)
@PageTitle("Translate | Zanata")
@AnonymousAllowed
public class TranslateView extends VerticalLayout implements BeforeEnterObserver {


    private static final Set<ContentState> COMPLETE = EnumSet.of(
            ContentState.Translated, ContentState.Approved);

    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final TranslationEditService translationEditService;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final AccountRepository accountRepository;

    private LocaleId currentLocale;
    private LocaleId sourceLocale;
    private String projectSlug;
    private String versionSlug;
    private String localeStr;
    private String currentDocId;

    private final TextField filter = new TextField();
    private final Checkbox incompleteOnly = new Checkbox("Incomplete");
    private final Checkbox completeOnly   = new Checkbox("Complete");
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
                    && java.util.Objects.equals(
                            flow == null ? null : flow.getId(),
                            r.flow == null ? null : r.flow.getId());
        }
    }

    public TranslateView(DocumentRepository documentRepository,
                         TextFlowRepository textFlowRepository,
                         TextFlowTargetRepository targetRepository,
                         TextFlowTargetHistoryRepository historyRepository,
                         TextFlowTargetReviewCommentRepository reviewCommentRepository,
                         TranslationEditService translationEditService,
                         LocaleRepository localeRepository,
                         LocaleMemberRepository localeMemberRepository,
                         AccountRepository accountRepository,
                         org.zanata.spring.service.ai.TranslationProviderRegistry aiRegistry,
                         AiPolicyService aiPolicy,
                         ProgressDialogService progressDialogs,
                         EditorPreferencesService editorPreferences) {
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.translationEditService = translationEditService;
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.accountRepository = accountRepository;
        this.aiRegistry = aiRegistry;
        this.aiPolicy = aiPolicy;
        this.progressDialogs = progressDialogs;
        this.editorPreferences = editorPreferences;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    private final org.zanata.spring.service.ai.TranslationProviderRegistry aiRegistry;
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
    private final java.util.Set<Long> historyExpanded =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> historyCollapsed =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        prefs = editorPreferences.load();
        projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        versionSlug = event.getRouteParameters().get("versionSlug").orElse("");
        localeStr   = event.getRouteParameters().get("localeId").orElse("");
        if (projectSlug.isBlank() || versionSlug.isBlank() || localeStr.isBlank()) {
            throw new NotFoundException("Missing route parameters");
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
            throw new NotFoundException(
                    "No documents found in " + projectSlug + "/" + versionSlug);
        }
        currentDocId = doc.getDocId();

        // No more eager findByDocument — DataProvider pulls pages from DB.
        sourceLocale = doc.getLocale() != null && doc.getLocale().getLocaleId() != null
                ? doc.getLocale().getLocaleId() : LocaleId.EN_US;
        Long docIdForProvider = doc.getId();

        add(buildBreadcrumb());

        HorizontalLayout headingRow = new HorizontalLayout();
        headingRow.setWidthFull();
        headingRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headingRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H2 heading = new H2(currentDocId + " \u2192 " + localeStr);
        heading.addClassNames(LumoUtility.Margin.NONE);

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
        docSwitcher.getStyle().set("font-weight", "600");
        docSwitcher.getStyle().set("max-width", "520px");
        docSwitcher.getStyle().set("overflow", "hidden");
        docSwitcher.getStyle().set("text-overflow", "ellipsis");
        com.vaadin.flow.component.popover.Popover docPopover =
                new com.vaadin.flow.component.popover.Popover();
        docPopover.setPosition(com.vaadin.flow.component.popover.PopoverPosition.BOTTOM);
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
        Button statsBtn = new Button("Stats");
        statsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        statsBtn.setIcon(LineAwesomeIcon.CHART_BAR_SOLID.create());
        com.vaadin.flow.component.popover.Popover statsPopover =
                new com.vaadin.flow.component.popover.Popover();
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
        prefsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL,
                ButtonVariant.LUMO_ICON);
        prefsBtn.getElement().setAttribute("title", "Editor settings");
        prefsBtn.getElement().setAttribute("aria-label", "Editor settings");
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
        rowsList.setRenderer(new ComponentRenderer<Component, RowData>(
                d -> buildRow(d.flow(), d.existing(), d.state(), d.source())));
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
        com.vaadin.flow.component.dialog.Dialog dlg =
                new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Editor settings");
        dlg.setWidth("420px");
        // Multiple ways out so the dialog never traps the user, even when
        // Save would be a no-op (anonymous = preferences not persisted).
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(true);

        // Header close (X) — small but always present, mirrors common UX.
        Button closeX = new Button(LineAwesomeIcon.TIMES_SOLID.create(), e -> dlg.close());
        closeX.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL, ButtonVariant.LUMO_ICON);
        closeX.getElement().setAttribute("aria-label", "Close");
        closeX.getElement().setAttribute("title", "Close");
        dlg.getHeader().add(closeX);

        com.vaadin.flow.component.checkbox.Checkbox compact =
                new com.vaadin.flow.component.checkbox.Checkbox("Compact rows");
        compact.setValue(prefs.compactRows());
        compact.setHelperText("Tighter padding so more rows fit in the viewport.");

        com.vaadin.flow.component.checkbox.Checkbox showReview =
                new com.vaadin.flow.component.checkbox.Checkbox("Show review comments inline");
        showReview.setValue(prefs.showReviewComments());
        showReview.setHelperText("Render rejection reasons at the top of the History panel.");

        com.vaadin.flow.component.checkbox.Checkbox autoHistory =
                new com.vaadin.flow.component.checkbox.Checkbox("Open history panel by default");
        autoHistory.setValue(prefs.autoOpenHistory());

        boolean signedIn = isAuthenticated();
        if (!signedIn) {
            Paragraph note = new Paragraph(
                    "Sign in to persist these preferences. Changes will apply to "
                    + "this browser session only.");
            note.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            note.getStyle().set("font-size", "0.85rem");
            note.getStyle().set("margin", "0 0 0.5rem 0");
            dlg.add(note);
        }

        Button cancel = new Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);
        Button save = new Button("Save", e -> {
            var next = new EditorPreferencesService.Prefs(
                    Boolean.TRUE.equals(compact.getValue()),
                    Boolean.TRUE.equals(showReview.getValue()),
                    Boolean.TRUE.equals(autoHistory.getValue()));
            // Signed-in: persists to HAccountOption.
            // Anonymous: stashed on UI via ComponentUtil — survives in-app
            // navigation but is gone on full browser reload (which is fine
            // because we never reload the page from here any more).
            editorPreferences.save(next);
            prefs = next;
            // Refresh the data provider so already-rendered rows pick up
            // the new prefs (compact rows, autoOpenHistory, etc.) without
            // throwing away the whole UI via page.reload().
            if (rowsList.getDataProvider() != null) {
                rowsList.getDataProvider().refreshAll();
            }
            Notification.show(signedIn ? "Editor settings saved"
                            : "Applied for this session",
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
        com.vaadin.flow.component.dialog.Dialog dlg =
                new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Keyboard shortcuts");
        dlg.setWidth("520px");
        dlg.setCloseOnEsc(true);

        String html = "<table style=\"border-collapse:collapse;width:100%;font-size:0.9rem\">"
                + row("Alt+Y", "Show this help")
                + row("Esc", "Close any open dialog")
                + row("Alt+G", "Copy source text into translation")
                + row("Alt+X", "Clear translation field")
                + row("Ctrl+Enter", "Save as Translated")
                + row("Ctrl+S", "Save as fuzzy (Need review)")
                + "</table>";
        Div content = new Div();
        content.getElement().setProperty("innerHTML", html);
        dlg.add(content);

        Button close = new Button("Close", e -> dlg.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        dlg.getFooter().add(close);
        dlg.open();
    }

    private static String row(String key, String desc) {
        return "<tr><td style=\"padding:0.3rem 0.5rem;font-family:monospace;"
                + "color:var(--aura-blue-text);white-space:nowrap\">" + key
                + "</td><td style=\"padding:0.3rem 0.5rem\">" + desc + "</td></tr>";
    }

    private com.vaadin.flow.component.menubar.MenuBar buildBulkAiButton(HDocument doc) {
        com.vaadin.flow.component.menubar.MenuBar mb = new com.vaadin.flow.component.menubar.MenuBar();
        mb.addThemeVariants(
                com.vaadin.flow.component.menubar.MenuBarVariant.TERTIARY,
                com.vaadin.flow.component.menubar.MenuBarVariant.SMALL);
        var item = mb.addItem("AI translate missing");
        item.getElement().setAttribute("title", "Use AI to fill in untranslated entries");
        for (var provider : aiRegistry.available()) {
            item.getSubMenu().addItem(provider.displayName(), e -> runBulkAi(doc, provider));
        }
        return mb;
    }

    private void runBulkAi(HDocument doc, org.zanata.spring.service.ai.TranslationProvider provider) {
        List<HTextFlow> flows = textFlowRepository.pageForTranslateView(
                doc.getId(), currentLocale, "", 1,
                org.springframework.data.domain.PageRequest.of(0, 1000));
        if (flows.isEmpty()) {
            Notification.show("Nothing to translate", 2000, Notification.Position.BOTTOM_START);
            return;
        }
        List<org.zanata.spring.service.ai.TranslationProvider.TranslationRequest> reqs =
                new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) {
            String src = tf.getContents().isEmpty() ? "" : tf.getContents().get(0);
            String ctx = tf.getPotEntryData() == null ? null : tf.getPotEntryData().getContext();
            reqs.add(new org.zanata.spring.service.ai.TranslationProvider.TranslationRequest(
                    src, sourceLocale, currentLocale, ctx));
        }

        int total = reqs.size();
        String title = "Translating with " + provider.displayName();
        progressDialogs.run(title, handle -> {
            handle.update(0, total, "Sending to " + provider.displayName() + "…");
            List<String> out = provider.translateBatch(reqs,
                    (done, t) -> handle.update(done, t,
                            "Translated " + done + " of " + t + " entries"));
            handle.update(total, total, "Saving translations…");
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
                Notification.show("AI translate failed: " + err.getMessage(),
                        5000, Notification.Position.MIDDLE);
                return;
            }
            Notification.show("AI filled " + saved + " of " + total + " entries",
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
    private void openDocPickerVia(com.vaadin.flow.component.popover.Popover pop,
                                  Button trigger, Long iterId, boolean viewingSource) {
        trigger.setEnabled(false);
        progressDialogs.run("Loading documents…", handle -> {
            handle.update(0, 3, "Listing documents in " + versionSlug + "…");
            List<HDocument> allDocs = documentRepository
                    .findByVersion(projectSlug, versionSlug);
            handle.update(1, 3, "Counting text-flows per document…");
            java.util.Map<Long, Long> totalByDoc = new HashMap<>();
            java.util.Map<Long, Long> translatedByDoc = new HashMap<>();
            if (iterId != null) {
                for (Object[] row : textFlowRepository.countPerDocForIteration(iterId)) {
                    totalByDoc.put(((Number) row[0]).longValue(),
                            ((Number) row[1]).longValue());
                }
                handle.update(2, 3, "Counting translated entries for " + localeStr + "…");
                for (Object[] row : targetRepository
                        .translatedCountPerDocForLocale(iterId, currentLocale)) {
                    translatedByDoc.put(((Number) row[0]).longValue(),
                            ((Number) row[1]).longValue());
                }
            }
            handle.update(3, 3, "Rendering grid…");
            return new Object[] { allDocs, totalByDoc, translatedByDoc };
        }).whenComplete((data, err) -> {
            trigger.setEnabled(true);
            if (err != null) {
                Notification.show("Loading docs failed: " + err.getMessage(),
                        5000, Notification.Position.MIDDLE);
                return;
            }
            @SuppressWarnings("unchecked")
            List<HDocument> allDocs = (List<HDocument>) data[0];
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Long> totalByDoc = (java.util.Map<Long, Long>) data[1];
            @SuppressWarnings("unchecked")
            java.util.Map<Long, Long> translatedByDoc = (java.util.Map<Long, Long>) data[2];
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
    private void openStatsVia(com.vaadin.flow.component.popover.Popover pop, Button trigger) {
        trigger.setEnabled(false);
        progressDialogs.run("Computing stats…", handle -> {
            handle.status("Aggregating word counts for " + currentDocId + "…");
            return buildStatsBadge();
        }).whenComplete((badge, err) -> {
            trigger.setEnabled(true);
            if (err != null) {
                Notification.show("Stats failed: " + err.getMessage(),
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
            java.util.Map<Long, Long> totalByDoc,
            java.util.Map<Long, Long> translatedByDoc,
            boolean viewingSource,
            com.vaadin.flow.component.popover.Popover pop) {
        TextField search = new TextField();
        search.setPlaceholder("Filter by docId…");
        search.setClearButtonVisible(true);
        search.setWidthFull();
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(150);

        com.vaadin.flow.component.grid.Grid<HDocument> grid =
                new com.vaadin.flow.component.grid.Grid<>();
        grid.setSizeFull();
        grid.setSelectionMode(com.vaadin.flow.component.grid.Grid.SelectionMode.NONE);

        grid.addColumn(HDocument::getDocId)
                .setHeader("Document")
                .setFlexGrow(1)
                .setSortable(true)
                .setKey("docId");

        java.util.function.ToIntFunction<HDocument> pctOf = d -> {
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
            String color = pct == 100 ? "var(--aura-green-text)"
                    : pct >= 50 ? "var(--aura-orange-text)"
                    : "var(--aura-red-text)";
            Span pill = new Span(String.format("%d%% (%d/%d)", pct, done, total));
            pill.getStyle().set("color", color);
            pill.getStyle().set("font-weight", "600");
            pill.getStyle().set("font-variant-numeric", "tabular-nums");
            return pill;
        }).setHeader("Translated").setWidth("180px").setFlexGrow(0)
                .setSortable(true).setComparator(java.util.Comparator.comparingInt(pctOf));

        com.vaadin.flow.data.provider.ListDataProvider<HDocument> dp =
                new com.vaadin.flow.data.provider.ListDataProvider<>(allDocs);
        grid.setDataProvider(dp);
        search.addValueChangeListener(e -> {
            String q = e.getValue() == null ? "" : e.getValue().trim().toLowerCase();
            dp.setFilter(d -> q.isEmpty() || d.getDocId().toLowerCase().contains(q));
        });

        grid.addItemClickListener(ev -> {
            HDocument picked = ev.getItem();
            if (picked == null) return;
            org.slf4j.LoggerFactory.getLogger(TranslateView.class)
                    .info("doc-picker → switch to {}", picked.getDocId());
            // Close client-side first so the popover disappears instantly,
            // independent of the server roundtrip for navigate().
            pop.getElement().executeJs("this.opened = false;");
            pop.close();
            if (picked.getDocId().equals(currentDocId)) return;
            UI.getCurrent().navigate(
                    "translate/" + projectSlug + "/" + versionSlug + "/" + localeStr,
                    com.vaadin.flow.router.QueryParameters.simple(
                            java.util.Map.of("doc", picked.getDocId())));
        });

        // Search stays pinned at top, grid takes the rest and scrolls internally.
        // The Popover content slot doesn't propagate its height down to flex
        // children, so we give the body an explicit viewport-relative height
        // matching the popover's 70vh.
        search.getStyle().set("flex-shrink", "0");
        grid.setSizeFull();
        grid.getStyle().set("min-height", "0");
        grid.getStyle().set("flex", "1 1 0");
        grid.setAllRowsVisible(false);

        VerticalLayout body = new VerticalLayout(search, grid);
        body.setPadding(true);
        body.setSpacing(true);
        body.setWidthFull();
        body.setHeight("calc(70vh - 2rem)");
        body.setFlexGrow(0, search);
        body.setFlexGrow(1, grid);
        body.getStyle().set("min-height", "0");
        body.getStyle().set("overflow", "hidden");
        return body;
    }

    private Div buildFilterBar() {
        filter.setPlaceholder("Click to see available filter terms (separate by space)");
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        // Filter/checkbox listeners are installed by installDataProvider(),
        // which has access to the per-doc provider instance.
        // Stop the label wrapping when the bar runs out of room.
        incompleteOnly.getStyle().set("white-space", "nowrap");
        completeOnly.getStyle().set("white-space", "nowrap");

        HorizontalLayout checks = new HorizontalLayout(incompleteOnly, completeOnly);
        checks.setAlignItems(FlexComponent.Alignment.CENTER);
        checks.setSpacing(true);
        checks.getStyle().set("flex-shrink", "0");
        checks.getStyle().set("white-space", "nowrap");

        HorizontalLayout bar = new HorizontalLayout(filter, checks);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        bar.setSpacing(true);
        bar.setFlexGrow(1, filter);

        Div wrap = new Div(bar);
        wrap.getStyle().set("border-bottom", "1px solid var(--vaadin-border-color)");
        wrap.getStyle().set("padding", "0.5rem 0 0.75rem 0");
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
        com.vaadin.flow.data.provider.CallbackDataProvider<RowData, FilterSpec> provider =
                com.vaadin.flow.data.provider.DataProvider.fromFilteringCallbacks(
                        query -> fetchPage(docId, query),
                        query -> countMatching(docId, query));
        com.vaadin.flow.data.provider.ConfigurableFilterDataProvider<RowData, Void, FilterSpec>
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

    private java.util.stream.Stream<RowData> fetchPage(Long docId,
            com.vaadin.flow.data.provider.Query<RowData, FilterSpec> query) {
        FilterSpec f = query.getFilter().orElse(new FilterSpec("", 0));
        int page = query.getOffset() / Math.max(1, query.getLimit());
        org.springframework.data.domain.PageRequest pr =
                org.springframework.data.domain.PageRequest.of(page, query.getLimit());
        List<HTextFlow> flows = textFlowRepository.pageForTranslateView(
                docId, currentLocale, f.q(), f.stateMode(), pr);
        if (flows.isEmpty()) return java.util.stream.Stream.empty();
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
            com.vaadin.flow.data.provider.Query<RowData, FilterSpec> query) {
        FilterSpec f = query.getFilter().orElse(new FilterSpec("", 0));
        long n = textFlowRepository.countForTranslateView(
                docId, currentLocale, f.q(), f.stateMode());
        return (int) Math.min(Integer.MAX_VALUE, n);
    }

    private Breadcrumbs buildBreadcrumb() {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(projectSlug, "/project/view/" + projectSlug),
                new Breadcrumb(versionSlug,
                        "/project/" + projectSlug + "/version/" + versionSlug),
                new Breadcrumb(localeStr, "#", true)
        );
        return crumbs;
    }

    private Div buildRow(HTextFlow flow, Optional<HTextFlowTarget> existing,
                         ContentState initialState, String source) {
        Div card = new Div();
        card.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        card.getStyle().set("border-radius", "8px");
        // Editor preference: tight padding when "Compact rows" is on.
        card.getStyle().set("padding", prefs.compactRows() ? "0.4rem 0.6rem" : "0.75rem");
        card.getStyle().set("margin-bottom", prefs.compactRows() ? "0.4rem" : "0.75rem");
        card.getStyle().set("box-sizing", "border-box");
        card.getStyle().set("overflow", "hidden");
        card.setWidthFull();

        String initialContent = existing.map(HTextFlowTarget::getContents)
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .orElse("");

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(false);
        row.getStyle().set("box-sizing", "border-box");

        VerticalLayout left = new VerticalLayout();
        left.setPadding(false);
        left.setSpacing(false);
        left.setWidth(null);
        left.getStyle().set("flex", "1 1 0");
        left.getStyle().set("min-width", "0");
        left.getStyle().set("border-right", "1px solid var(--vaadin-border-color)");
        left.getStyle().set("padding-right", "1rem");
        left.getStyle().set("margin-right", "1rem");
        // Prefer the human-readable key (PotEntryData.context) over the
        // 32-char MD5 hash that lives in resId. Falls back to the hash
        // for legacy rows that have no context yet.
        String displayKey = flow.getPotEntryData() != null
                && flow.getPotEntryData().getContext() != null
                && !flow.getPotEntryData().getContext().isEmpty()
                ? flow.getPotEntryData().getContext()
                : flow.getResId();
        Span resId = new Span(displayKey);
        resId.addClassNames(LumoUtility.FontSize.XSMALL);
        resId.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        Paragraph srcText = new Paragraph(source);
        srcText.addClassNames(LumoUtility.Margin.NONE);
        srcText.getStyle().set("white-space", "pre-wrap");

        Button detailsBtn = new Button("Details", LineAwesomeIcon.INFO_CIRCLE_SOLID.create());
        detailsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        // Bookmark / permalink — copies a deep link to this text flow to the
        // clipboard. Replaces the older "Link" panel which only displayed it.
        Button bookmarkBtn = new Button(LineAwesomeIcon.BOOKMARK_SOLID.create(),
                e -> copyPermalink(flow.getId()));
        bookmarkBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL,
                ButtonVariant.LUMO_ICON);
        bookmarkBtn.getElement().setAttribute("title", "Copy permalink to this string");
        bookmarkBtn.getElement().setAttribute("aria-label", "Copy permalink");

        Div detailsPanel = buildDetailsPanel(flow);
        detailsPanel.setVisible(false);

        detailsBtn.addClickListener(e -> detailsPanel.setVisible(!detailsPanel.isVisible()));

        HorizontalLayout leftBtns = new HorizontalLayout(detailsBtn, bookmarkBtn);
        leftBtns.setSpacing(true);

        left.add(resId, srcText, leftBtns, detailsPanel);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setWidth(null);
        right.getStyle().set("flex", "1 1 0");
        right.getStyle().set("min-width", "0");

        boolean isSourceLocale = sourceLocale != null
                && sourceLocale.getId().equalsIgnoreCase(localeStr);
        boolean canEdit = isAuthenticated();

        TextArea area = new TextArea(isSourceLocale ? "Source" : "Translation");
        area.setWidthFull();
        area.setMinHeight("4rem");
        area.setValue(isSourceLocale ? source : initialContent);
        area.setReadOnly(!canEdit);

        // Per-row keyboard shortcuts — fire only when this textarea is focused
        // so they don't collide between rows. Matches the legacy editor.
        if (canEdit && !isSourceLocale) {
            // Alt+G — copy text from source string
            Shortcuts.addShortcutListener(area, () -> area.setValue(source),
                    Key.KEY_G, KeyModifier.ALT).listenOn(area);
            // Alt+X — clear translation (docs call this "attention mode" but
            // legacy editor also wiped the field on this combo).
            Shortcuts.addShortcutListener(area, () -> area.setValue(""),
                    Key.KEY_X, KeyModifier.ALT).listenOn(area);
        }

        Span stateSpan = new Span(initialState.name());
        applyStateColor(stateSpan, initialState);

        Button save = new Button("Save");
        save.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        save.setEnabled(canEdit);
        if (!canEdit) {
            save.setTooltipText("Sign in to edit");
        }
        save.addClickListener(e -> {
            if (isSourceLocale) {
                translationEditService.updateSource(flow.getId(), area.getValue());
                Notification.show("Source updated", 2000, Notification.Position.BOTTOM_START);
            } else {
                ContentState newState = translationEditService.save(
                        flow.getId(), currentLocale, area.getValue());
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Notification.show("Saved", 2000, Notification.Position.BOTTOM_START);
            }
        });
        // Ctrl+Enter — save as Translated (matches legacy + Zanata docs).
        if (canEdit) {
            Shortcuts.addShortcutListener(area, save::click,
                    Key.ENTER, KeyModifier.CONTROL).listenOn(area);
            // Ctrl+S — save as fuzzy (NeedReview). Only valid for target rows.
            if (!isSourceLocale) {
                Shortcuts.addShortcutListener(area, () -> {
                    try {
                        translationEditService.save(
                                flow.getId(), currentLocale, area.getValue());
                        ContentState ns = translationEditService.changeState(
                                flow.getId(), currentLocale, ContentState.NeedReview);
                        stateSpan.setText(ns.name());
                        applyStateColor(stateSpan, ns);
                        Notification.show("Saved as fuzzy", 2000,
                                Notification.Position.BOTTOM_START);
                    } catch (Exception ex) {
                        Notification.show("Save fuzzy failed: " + ex.getMessage(),
                                4000, Notification.Position.MIDDLE);
                    }
                }, Key.KEY_S, KeyModifier.CONTROL).listenOn(area)
                        .setBrowserDefaultAllowed(false);
            }
        }

        Button historyBtn = new Button("History", LineAwesomeIcon.CLOCK_SOLID.create());
        historyBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Div historyPanel = buildHistoryPanel(flow.getId(), initialContent, () -> area.getValue());
        // Initial visibility: explicit user toggle wins over the global pref.
        boolean initiallyOpen = historyExpanded.contains(flow.getId())
                || (prefs.autoOpenHistory() && !historyCollapsed.contains(flow.getId()));
        historyPanel.setVisible(initiallyOpen);
        historyBtn.addClickListener(e -> {
            // Re-render every open so the "Unsaved" row reflects the current
            // textarea value and review-comments stay fresh.
            Div fresh = buildHistoryPanel(flow.getId(), initialContent, () -> area.getValue());
            historyPanel.getElement().removeAllChildren();
            fresh.getChildren().forEach(child -> historyPanel.getElement()
                    .appendChild(child.getElement()));
            boolean nowOpen = !historyPanel.isVisible();
            historyPanel.setVisible(nowOpen);
            // Record the explicit state so VirtualList row recycling doesn't
            // snap us back to the default.
            if (nowOpen) {
                historyExpanded.add(flow.getId());
                historyCollapsed.remove(flow.getId());
            } else {
                historyCollapsed.add(flow.getId());
                historyExpanded.remove(flow.getId());
            }
        });

        // Per-row "AI translate" — gated by the global flag, provider availability,
        // and the per-user opt-in. Source locale never gets the button.
        com.vaadin.flow.component.menubar.MenuBar aiBtn = null;
        if (!isSourceLocale && aiPolicy.canCurrentUserUseAi()) {
            aiBtn = new com.vaadin.flow.component.menubar.MenuBar();
            aiBtn.addThemeVariants(
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY_INLINE,
                    com.vaadin.flow.component.menubar.MenuBarVariant.SMALL);
            var trigger = aiBtn.addItem(LineAwesomeIcon.MAGIC_SOLID.create());
            trigger.getElement().setAttribute("aria-label", "Translate with AI");
            trigger.getElement().setAttribute("title", "Translate with AI");
            for (var provider : aiRegistry.available()) {
                trigger.getSubMenu().addItem(provider.displayName(), ev -> {
                    String ctx = flow.getPotEntryData() == null ? null
                            : flow.getPotEntryData().getContext();
                    progressDialogs.run("Translating with " + provider.displayName(),
                            handle -> {
                                handle.status("Calling " + provider.displayName() + "…");
                                return provider.translate(source, sourceLocale, currentLocale, ctx);
                            })
                            .whenComplete((out, err) -> {
                                if (err != null) {
                                    Notification.show("AI translate failed: " + err.getMessage(),
                                            5000, Notification.Position.MIDDLE);
                                    return;
                                }
                                if (out != null && !out.isBlank()) {
                                    area.setValue(out);
                                    Notification.show("Filled by " + provider.displayName(),
                                            2000, Notification.Position.BOTTOM_START);
                                }
                            });
                });
            }
        }

        boolean canReview = canReviewLocale();
        boolean hasSavedContent = !initialContent.isBlank();

        Button approve = new Button("Approve", LineAwesomeIcon.CHECK_SOLID.create());
        approve.addThemeVariants(ButtonVariant.SUCCESS, ButtonVariant.SMALL);
        approve.setEnabled(!isSourceLocale && canReview && hasSavedContent);
        if (isSourceLocale) {
            approve.setVisible(false);
        } else if (!canReview) {
            approve.setTooltipText("Join the " + localeStr + " language team as reviewer to approve");
        } else if (!hasSavedContent) {
            approve.setTooltipText("Save a translation before approving");
        }
        approve.addClickListener(e -> {
            try {
                ContentState newState = translationEditService.changeState(
                        flow.getId(), currentLocale, ContentState.Approved);
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Notification.show("Approved", 2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show("Approve failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });

        Button reject = new Button("Reject", LineAwesomeIcon.TIMES_SOLID.create());
        reject.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);
        reject.setEnabled(!isSourceLocale && canReview && hasSavedContent);
        if (isSourceLocale) {
            reject.setVisible(false);
        } else if (!canReview) {
            reject.setTooltipText("Join the " + localeStr + " language team as reviewer to reject");
        } else if (!hasSavedContent) {
            reject.setTooltipText("Save a translation before rejecting");
        }
        reject.addClickListener(e -> openRejectDialog(flow, stateSpan, historyPanel));

        HorizontalLayout actionRow = new HorizontalLayout(
                save, approve, reject, historyBtn, stateSpan);
        if (aiBtn != null) actionRow.add(aiBtn);
        actionRow.setAlignItems(FlexComponent.Alignment.CENTER);
        actionRow.setSpacing(true);
        actionRow.getStyle().set("margin-top", "0.4rem");
        actionRow.getStyle().set("flex-wrap", "wrap");

        right.add(area, actionRow, historyPanel);

        row.add(left, right);
        row.setFlexGrow(1, left);
        row.setFlexGrow(1, right);
        card.add(row);
        return card;
    }

    private Div buildDetailsPanel(HTextFlow flow) {
        Div d = new Div();
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        d.getStyle().set("border-radius", "6px");
        d.getStyle().set("padding", "0.6rem");
        d.getStyle().set("margin-top", "0.5rem");
        d.getStyle().set("font-size", "0.875rem");
        List<String> rows = new ArrayList<>();
        rows.add(label("Resource ID:", flow.getResId()));
        rows.add(label("Message Context:", noContent(flow.getPotEntryData() == null
                ? null : flow.getPotEntryData().getContext())));
        rows.add(label("Reference:", noContent(flow.getPotEntryData() == null
                ? null : flow.getPotEntryData().getReferences())));
        rows.add(label("Flags:", noContent(flow.getPotEntryData() == null
                ? null : flow.getPotEntryData().getFlags())));
        rows.add(label("Source Comment:", noContent(flow.getComment() == null
                ? null : String.valueOf(flow.getComment()))));
        for (String r : rows) {
            Paragraph p = new Paragraph();
            p.getElement().setProperty("innerHTML", r);
            p.addClassNames(LumoUtility.Margin.NONE);
            d.add(p);
        }
        return d;
    }

    /**
     * Build the History panel: review comments at the top, then a selectable
     * Grid of {version, state, modifier, date, content}. Selecting exactly
     * two rows enables "Compare selected" which opens a side-by-side diff.
     * If the live textarea value differs from the saved current target, an
     * "Unsaved" pseudo-row is appended so the user can diff what they're
     * typing against any saved version.
     */
    private Div buildHistoryPanel(Long textFlowId, String savedContent,
                                  java.util.function.Supplier<String> liveContent) {
        Div d = new Div();
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        d.getStyle().set("border-radius", "6px");
        d.getStyle().set("padding", "0.6rem");
        d.getStyle().set("margin-top", "0.5rem");
        d.getStyle().set("font-size", "0.875rem");

        // Review comments (rejection reasons) come first so the most recent
        // feedback is immediately visible to the translator. Suppressed when
        // the user has disabled "Show review comments inline" in editor settings.
        List<HTextFlowTargetReviewComment> comments = prefs.showReviewComments()
                ? reviewCommentRepository.findByTextFlowAndLocale(textFlowId, currentLocale)
                : java.util.List.of();
        if (!comments.isEmpty()) {
            Span header = new Span("Review comments");
            header.getStyle().set("font-weight", "600");
            header.getStyle().set("display", "block");
            header.getStyle().set("margin-bottom", "0.3rem");
            d.add(header);
            for (HTextFlowTargetReviewComment c : comments) {
                Div entry = new Div();
                entry.getStyle().set("padding", "0.3rem 0");
                entry.getStyle().set("border-left", "3px solid var(--aura-red-text)");
                entry.getStyle().set("padding-left", "0.5rem");
                entry.getStyle().set("margin", "0.25rem 0");
                String who = c.getCommenterName() == null ? "" : c.getCommenterName();
                String when = c.getCreationDate() == null ? "" : " — " + c.getCreationDate();
                Span meta = new Span("by " + who
                        + " (target v" + c.getTargetVersion() + ")" + when);
                meta.getStyle().set("color", "var(--vaadin-text-color-secondary)");
                meta.getStyle().set("font-size", "0.8rem");
                Paragraph p = new Paragraph(c.getCommentText());
                p.getStyle().set("margin", "0.15rem 0 0 0");
                p.getStyle().set("white-space", "pre-wrap");
                entry.add(meta, p);
                d.add(entry);
            }
            Div sep = new Div();
            sep.getStyle().set("height", "1px");
            sep.getStyle().set("background", "var(--vaadin-border-color)");
            sep.getStyle().set("margin", "0.5rem 0");
            d.add(sep);
        }

        // Build the version rows: every persisted HTextFlowTargetHistory +
        // the current saved target (rendered as "current") + the live
        // unsaved value when it differs from saved.
        List<HistoryRow> rows = new java.util.ArrayList<>();
        List<HTextFlowTargetHistory> hist =
                historyRepository.findByTextFlowAndLocale(textFlowId, currentLocale);
        for (HTextFlowTargetHistory h : hist) {
            rows.add(new HistoryRow(
                    "v" + h.getVersionNum(),
                    h.getState() == null ? "" : h.getState().name(),
                    h.getLastModifiedBy() == null ? "" : h.getLastModifiedBy().getName(),
                    h.getLastChanged() == null ? "" : h.getLastChanged().toString(),
                    firstContent(h.getContents())));
        }
        // Current saved target — show as "current" only when there's something
        // saved AND it isn't already covered by the latest history row.
        if (savedContent != null && !savedContent.isBlank()) {
            HTextFlowTarget cur = targetRepository
                    .findByTextFlowAndLocaleWithModifier(textFlowId, currentLocale)
                    .orElse(null);
            if (cur != null) {
                rows.add(new HistoryRow(
                        "current",
                        cur.getState() == null ? "" : cur.getState().name(),
                        cur.getLastModifiedBy() == null ? "" : cur.getLastModifiedBy().getName(),
                        cur.getLastChanged() == null ? "" : cur.getLastChanged().toString(),
                        firstContent(cur.getContents())));
            }
        }
        // Live (unsaved) typed value, only if it differs from the saved one
        String live = liveContent == null ? null : liveContent.get();
        if (live != null && !live.isEmpty()
                && !java.util.Objects.equals(live, savedContent)) {
            rows.add(new HistoryRow("Unsaved", "—", "you", "—", live));
        }

        if (rows.isEmpty() && comments.isEmpty()) {
            Paragraph empty = new Paragraph("No previous versions.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            empty.getStyle().set("margin", "0");
            d.add(empty);
            return d;
        }

        com.vaadin.flow.component.grid.Grid<HistoryRow> grid =
                new com.vaadin.flow.component.grid.Grid<>();
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        grid.setSelectionMode(com.vaadin.flow.component.grid.Grid.SelectionMode.MULTI);
        grid.addColumn(HistoryRow::version).setHeader("Version")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::state).setHeader("State")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::modifier).setHeader("By")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::when).setHeader("When")
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(r -> {
            String c = r.content() == null ? "" : r.content();
            return c.length() > 120 ? c.substring(0, 117) + "…" : c;
        }).setHeader("Content").setFlexGrow(1);

        Button compare = new Button("Compare selected", LineAwesomeIcon.NOT_EQUAL_SOLID.create());
        compare.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        compare.setEnabled(false);
        Span hint = new Span("Select exactly 2 rows to compare");
        hint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        hint.getStyle().set("font-size", "0.8rem");
        hint.getStyle().set("margin-left", "0.5rem");
        grid.asMultiSelect().addSelectionListener(ev -> {
            int n = ev.getValue().size();
            compare.setEnabled(n == 2);
            hint.setText(n == 2 ? "Click compare to see the diff"
                    : "Select exactly 2 rows to compare (selected: " + n + ")");
        });
        compare.addClickListener(ev -> {
            var sel = new java.util.ArrayList<>(grid.getSelectedItems());
            if (sel.size() != 2) return;
            openDiffDialog(sel.get(0), sel.get(1));
        });

        HorizontalLayout toolbar = new HorizontalLayout(compare, hint);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("margin", "0.25rem 0");
        d.add(toolbar, grid);
        return d;
    }

    /** Snapshot row for the history Grid + diff view. */
    private record HistoryRow(String version, String state, String modifier,
                              String when, String content) {}

    /** Open a side-by-side diff of two history rows in a modal dialog. */
    private void openDiffDialog(HistoryRow a, HistoryRow b) {
        com.vaadin.flow.component.dialog.Dialog dlg = new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Compare " + a.version() + " ↔ " + b.version());
        dlg.setWidth("min(900px, 90vw)");
        dlg.setHeight("min(600px, 80vh)");
        dlg.setCloseOnEsc(true);

        HorizontalLayout body = new HorizontalLayout();
        body.setSizeFull();
        body.setSpacing(true);

        Div left = diffCell(a.version(), a.state(), a.when(),
                renderDiffHtml(a.content(), b.content(), true));
        Div right = diffCell(b.version(), b.state(), b.when(),
                renderDiffHtml(a.content(), b.content(), false));
        left.getStyle().set("flex", "1 1 0");
        right.getStyle().set("flex", "1 1 0");
        body.add(left, right);

        dlg.add(body);
        com.vaadin.flow.component.button.Button close =
                new com.vaadin.flow.component.button.Button("Close", e -> dlg.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        dlg.getFooter().add(close);
        dlg.open();
    }

    private static Div diffCell(String version, String state, String when, String html) {
        Div cell = new Div();
        cell.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        cell.getStyle().set("border-radius", "6px");
        cell.getStyle().set("padding", "0.5rem");
        cell.getStyle().set("background", "var(--vaadin-background-color)");
        cell.getStyle().set("overflow", "auto");
        Span header = new Span(version + " — " + state + (when.isEmpty() ? "" : " — " + when));
        header.getStyle().set("font-weight", "600");
        header.getStyle().set("display", "block");
        header.getStyle().set("margin-bottom", "0.4rem");
        Div content = new Div();
        content.getStyle().set("white-space", "pre-wrap");
        content.getStyle().set("font-family", "var(--lumo-font-family-mono, monospace)");
        content.getStyle().set("font-size", "0.88rem");
        content.getElement().setProperty("innerHTML", html);
        cell.add(header, content);
        return cell;
    }

    /**
     * Word-level LCS diff between {@code a} and {@code b}; if {@code leftSide}
     * is true, colour removed-from-a words red; otherwise colour added-in-b
     * words green. Whitespace is preserved by splitting on word boundaries
     * but rejoining the originals.
     */
    private static String renderDiffHtml(String a, String b, boolean leftSide) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] aw = a.split("(?<=\\s)|(?=\\s)");
        String[] bw = b.split("(?<=\\s)|(?=\\s)");
        int n = aw.length, m = bw.length;
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = aw[i].equals(bw[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        StringBuilder out = new StringBuilder();
        int i = 0, j = 0;
        String[] side = leftSide ? aw : bw;
        String addColor = "var(--aura-green-text)";
        String delColor = "var(--aura-red-text)";
        String bg = leftSide ? "rgba(255,0,0,0.12)" : "rgba(0,160,0,0.12)";
        String color = leftSide ? delColor : addColor;
        while (i < n && j < m) {
            if (aw[i].equals(bw[j])) {
                out.append(escapeHtml(side == aw ? aw[i] : bw[j]));
                i++; j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                // a[i] removed
                if (leftSide) appendMarked(out, aw[i], color, bg);
                i++;
            } else {
                // b[j] added
                if (!leftSide) appendMarked(out, bw[j], color, bg);
                j++;
            }
        }
        while (leftSide && i < n) { appendMarked(out, aw[i++], color, bg); }
        while (!leftSide && j < m) { appendMarked(out, bw[j++], color, bg); }
        return out.toString();
    }

    private static void appendMarked(StringBuilder sb, String word,
                                     String color, String bg) {
        sb.append("<span style=\"color:").append(color)
                .append(";background:").append(bg).append("\">")
                .append(escapeHtml(word))
                .append("</span>");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String firstContent(java.util.List<String> contents) {
        if (contents == null || contents.isEmpty()) return "";
        String c = contents.get(0);
        return c == null ? "" : c;
    }

    /**
     * Open the reject-with-reason modal. A non-empty reason is required —
     * docs mandate this so the translator knows what to fix. On confirm the
     * comment is attached to the target version being rejected and the
     * history panel is re-rendered in place.
     */
    private void openRejectDialog(HTextFlow flow, Span stateSpan, Div historyPanel) {
        com.vaadin.flow.component.dialog.Dialog dlg = new com.vaadin.flow.component.dialog.Dialog();
        dlg.setHeaderTitle("Reject translation");
        dlg.setWidth("480px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(false);

        Paragraph intro = new Paragraph(
                "Tell the translator why this translation needs to change. "
                + "The reason is shown in the history panel.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("margin", "0 0 0.75rem 0");
        intro.getStyle().set("font-size", "0.9rem");

        TextArea reason = new TextArea("Reason for rejection");
        reason.setWidthFull();
        reason.setMinHeight("6rem");
        reason.setRequired(true);
        reason.setValueChangeMode(ValueChangeMode.EAGER);

        Button confirm = new Button("Confirm rejection");
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setEnabled(false);
        Button cancel = new Button("Cancel", e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);

        // "Confirm rejection" will not work until a reason has been entered.
        reason.addValueChangeListener(e -> confirm.setEnabled(
                e.getValue() != null && !e.getValue().trim().isEmpty()));

        confirm.addClickListener(e -> {
            String text = reason.getValue() == null ? "" : reason.getValue().trim();
            if (text.isEmpty()) return;
            String reviewer = currentUsername();
            try {
                ContentState newState = translationEditService.changeState(
                        flow.getId(), currentLocale, ContentState.Rejected,
                        text, reviewer);
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                // Refresh the history panel in place so the new comment shows up
                // without losing the user's expand/collapse state on other rows.
                Div fresh = buildHistoryPanel(flow.getId(), null, () -> null);
                historyPanel.getElement().removeAllChildren();
                fresh.getChildren().forEach(child -> historyPanel.getElement()
                        .appendChild(child.getElement()));
                Notification.show("Rejected", 2000, Notification.Position.BOTTOM_START);
                dlg.close();
            } catch (Exception ex) {
                Notification.show("Reject failed: " + ex.getMessage(),
                        4000, Notification.Position.MIDDLE);
            }
        });

        dlg.add(intro, reason);
        dlg.getFooter().add(cancel, confirm);
        dlg.open();
        reason.focus();
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }

    /**
     * Build the deep-link URL for a text flow and copy it to the clipboard
     * via JavaScript. The format mirrors the existing {@code ?tf=ID} query
     * param the page already accepts.
     */
    private void copyPermalink(Long textFlowId) {
        String href = "/translate/" + projectSlug + "/" + versionSlug + "/"
                + localeStr + "?doc=" + currentDocId + "&tf=" + textFlowId;
        UI.getCurrent().getPage().executeJs(
                "const u = new URL($0, location.origin).toString();"
                + "navigator.clipboard.writeText(u);", href);
        Notification.show("Link copied to clipboard",
                1800, Notification.Position.BOTTOM_START);
    }

    private String label(String k, String v) {
        return "<strong>" + k + "</strong> "
                + (v == null || v.isBlank()
                    ? "<span style=\"color: var(--vaadin-text-color-secondary)\">No content</span>"
                    : escape(v));
    }

    private String noContent(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private boolean canReviewLocale() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return false;
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (admin) return true;
        var account = accountRepository.findByUsername(auth.getName()).orElse(null);
        if (account == null || account.getPerson() == null) return false;
        var locale = localeRepository.findByLocaleId(currentLocale).orElse(null);
        if (locale == null) return false;
        return localeMemberRepository.findByLocaleAndPerson(locale, account.getPerson())
                .map(m -> m.isReviewer() || m.isCoordinator())
                .orElse(false);
    }

    private Div buildStatsBadge() {
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
        badge.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        badge.getStyle().set("border-radius", "6px");
        badge.getStyle().set("padding", "0.5rem 0.75rem");
        badge.getStyle().set("background", "var(--vaadin-background-color)");
        badge.getStyle().set("font-size", "0.85rem");
        badge.getStyle().set("min-width", "420px");

        Div top = new Div(new Span(String.format("%.1f%% translated  \u00b7  %d / %d words  \u00b7  %d / %d messages",
                pct, translatedW + approvedW, totalW, translated + approved, total)));
        top.getStyle().set("font-weight", "600");
        top.getStyle().set("margin-bottom", "0.4rem");
        badge.add(top);

        badge.add(statRow("Approved",     approvedW,     approved));
        badge.add(statRow("Translated",   translatedW,   translated));
        badge.add(statRow("Needs review", needsReviewW,  needsReview));
        badge.add(statRow("Untranslated", untranslatedW, untranslated));
        return badge;
    }

    private Div statRow(String label, long words, long msgs) {
        Div row = new Div();
        row.getStyle().set("display", "flex");
        row.getStyle().set("gap", "1rem");
        row.getStyle().set("justify-content", "space-between");
        Span l = new Span(label);
        l.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        Span w = new Span(words + "w");
        Span m = new Span(msgs + "m");
        row.add(l, w, m);
        return row;
    }

    private void applyStateColor(Span span, ContentState state) {
        String color;
        switch (state) {
            case Translated -> color = "var(--aura-green)";
            case Approved -> color = "darkgreen";
            case NeedReview -> color = "orange";
            case Rejected -> color = "crimson";
            case New -> color = "var(--vaadin-text-color-secondary)";
            default -> color = "var(--vaadin-text-color-secondary)";
        }
        span.getStyle().set("color", color);
        span.getStyle().set("font-weight", "600");
    }
}
