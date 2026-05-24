package org.zanata.spring.vaadin.translate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
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
import org.zanata.spring.vaadin.ExploreView;
import org.zanata.spring.vaadin.MainLayout;
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
    private final TranslationEditService translationEditService;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final AccountRepository accountRepository;

    private List<HTextFlow> flows = List.of();
    private LocaleId currentLocale;
    private String projectSlug;
    private String versionSlug;
    private String localeStr;
    private String currentDocId;

    private final TextField filter = new TextField();
    private final Checkbox incompleteOnly = new Checkbox("Incomplete");
    private final Checkbox completeOnly   = new Checkbox("Complete");
    private final Div rowsContainer = new Div();

    public TranslateView(DocumentRepository documentRepository,
                         TextFlowRepository textFlowRepository,
                         TextFlowTargetRepository targetRepository,
                         TextFlowTargetHistoryRepository historyRepository,
                         TranslationEditService translationEditService,
                         LocaleRepository localeRepository,
                         LocaleMemberRepository localeMemberRepository,
                         AccountRepository accountRepository) {
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.translationEditService = translationEditService;
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.accountRepository = accountRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
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

        flows = textFlowRepository.findByDocument(doc.getId());

        add(buildBreadcrumb());

        HorizontalLayout headingRow = new HorizontalLayout();
        headingRow.setWidthFull();
        headingRow.setAlignItems(FlexComponent.Alignment.CENTER);
        headingRow.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H2 heading = new H2(currentDocId + " \u2192 " + localeStr);
        heading.addClassNames(LumoUtility.Margin.NONE);

        // Doc switcher — visible when the iteration has more than one doc.
        List<HDocument> allDocs = documentRepository.findByVersion(projectSlug, versionSlug);
        com.vaadin.flow.component.select.Select<String> docSwitcher = null;
        if (allDocs.size() > 1) {
            docSwitcher = new com.vaadin.flow.component.select.Select<>();
            docSwitcher.setLabel("Document");
            docSwitcher.setItems(allDocs.stream().map(HDocument::getDocId).toList());
            docSwitcher.setValue(currentDocId);
            docSwitcher.setWidth("260px");
            docSwitcher.addValueChangeListener(e -> {
                if (e.getValue() == null || e.getValue().equals(currentDocId)) return;
                UI.getCurrent().navigate(
                        "translate/" + projectSlug + "/" + versionSlug + "/" + localeStr,
                        com.vaadin.flow.router.QueryParameters.simple(
                                java.util.Map.of("doc", e.getValue())));
            });
        }

        Button statsBtn = new Button("Stats");
        statsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        statsBtn.setIcon(LineAwesomeIcon.CHART_BAR_SOLID.create());
        com.vaadin.flow.component.popover.Popover statsPopover =
                new com.vaadin.flow.component.popover.Popover();
        statsPopover.setTarget(statsBtn);
        statsPopover.add(buildStatsBadge());
        statsPopover.setWidth("420px");

        HorizontalLayout headingRight = new HorizontalLayout();
        headingRight.setAlignItems(FlexComponent.Alignment.CENTER);
        headingRight.setSpacing(true);
        if (docSwitcher != null) {
            headingRight.add(docSwitcher);
        }
        headingRight.add(statsBtn);

        headingRow.add(heading, headingRight);
        add(headingRow, statsPopover);

        add(buildFilterBar());

        rowsContainer.setWidthFull();
        rowsContainer.getStyle().set("max-width", "100%");
        rowsContainer.getStyle().set("overflow-x", "hidden");
        add(rowsContainer);

        refreshRows();
    }

    private Div buildFilterBar() {
        filter.setPlaceholder("Click to see available filter terms (separate by space)");
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        filter.addValueChangeListener(e -> refreshRows());

        incompleteOnly.addValueChangeListener(e -> refreshRows());
        completeOnly.addValueChangeListener(e -> refreshRows());
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

    private void refreshRows() {
        rowsContainer.removeAll();
        String q = filter.getValue() == null ? "" : filter.getValue().trim().toLowerCase();
        boolean wantIncomplete = incompleteOnly.getValue();
        boolean wantComplete   = completeOnly.getValue();

        for (HTextFlow flow : flows) {
            String source = flow.getContents().isEmpty() ? "" : flow.getContents().get(0);
            String contextKey = flow.getPotEntryData() == null
                    ? null : flow.getPotEntryData().getContext();
            if (!q.isEmpty()
                    && !source.toLowerCase().contains(q)
                    && !flow.getResId().toLowerCase().contains(q)
                    && (contextKey == null
                        || !contextKey.toLowerCase().contains(q))) {
                continue;
            }
            Optional<HTextFlowTarget> existing =
                    targetRepository.findByTextFlowAndLocale(flow.getId(), currentLocale);
            ContentState state = existing.map(HTextFlowTarget::getState).orElse(ContentState.New);
            boolean complete = COMPLETE.contains(state);
            if (wantIncomplete && complete) continue;
            if (wantComplete && !complete) continue;

            rowsContainer.add(buildRow(flow, existing, state, source));
        }
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
        card.getStyle().set("padding", "0.75rem");
        card.getStyle().set("margin-bottom", "0.75rem");
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
        detailsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Button linkBtn = new Button("Link", LineAwesomeIcon.STAR.create());
        linkBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Div detailsPanel = buildDetailsPanel(flow);
        detailsPanel.setVisible(false);
        Div linkPanel = buildLinkPanel(flow);
        linkPanel.setVisible(false);

        detailsBtn.addClickListener(e -> detailsPanel.setVisible(!detailsPanel.isVisible()));
        linkBtn.addClickListener(e -> linkPanel.setVisible(!linkPanel.isVisible()));

        HorizontalLayout leftBtns = new HorizontalLayout(detailsBtn, linkBtn);
        leftBtns.setSpacing(true);

        left.add(resId, srcText, leftBtns, detailsPanel, linkPanel);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setWidth(null);
        right.getStyle().set("flex", "1 1 0");
        right.getStyle().set("min-width", "0");

        boolean canEdit = isAuthenticated();

        TextArea area = new TextArea("Translation");
        area.setWidthFull();
        area.setMinHeight("4rem");
        area.setValue(initialContent);
        area.setReadOnly(!canEdit);

        Span stateSpan = new Span(initialState.name());
        applyStateColor(stateSpan, initialState);

        Button save = new Button("Save");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
        save.setEnabled(canEdit);
        if (!canEdit) {
            save.setTooltipText("Sign in to edit translations");
        }
        save.addClickListener(e -> {
            ContentState newState = translationEditService.save(
                    flow.getId(), currentLocale, area.getValue());
            stateSpan.setText(newState.name());
            applyStateColor(stateSpan, newState);
            Notification.show("Saved", 2000, Notification.Position.BOTTOM_START);
        });

        Button historyBtn = new Button("History", LineAwesomeIcon.CLOCK_SOLID.create());
        historyBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        Div historyPanel = buildHistoryPanel(flow.getId());
        historyPanel.setVisible(false);
        historyBtn.addClickListener(e -> historyPanel.setVisible(!historyPanel.isVisible()));

        boolean canReview = canReviewLocale();
        boolean hasSavedContent = !initialContent.isBlank();

        Button approve = new Button("Approve", LineAwesomeIcon.CHECK_SOLID.create());
        approve.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
        approve.setEnabled(canReview && hasSavedContent);
        if (!canReview) {
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
        reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
        reject.setEnabled(canReview && hasSavedContent);
        if (!canReview) {
            reject.setTooltipText("Join the " + localeStr + " language team as reviewer to reject");
        } else if (!hasSavedContent) {
            reject.setTooltipText("Save a translation before rejecting");
        }
        reject.addClickListener(e -> {
            try {
                ContentState newState = translationEditService.changeState(
                        flow.getId(), currentLocale, ContentState.Rejected);
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Notification.show("Rejected", 2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show("Reject failed: " + ex.getMessage(), 4000, Notification.Position.MIDDLE);
            }
        });

        HorizontalLayout actionRow = new HorizontalLayout(
                save, approve, reject, historyBtn, stateSpan);
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

    private Div buildHistoryPanel(Long textFlowId) {
        Div d = new Div();
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        d.getStyle().set("border-radius", "6px");
        d.getStyle().set("padding", "0.6rem");
        d.getStyle().set("margin-top", "0.5rem");
        d.getStyle().set("font-size", "0.875rem");

        List<HTextFlowTargetHistory> hist =
                historyRepository.findByTextFlowAndLocale(textFlowId, currentLocale);
        if (hist.isEmpty()) {
            Paragraph empty = new Paragraph("No previous versions.");
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            empty.getStyle().set("margin", "0");
            d.add(empty);
            return d;
        }
        for (HTextFlowTargetHistory h : hist) {
            Div entry = new Div();
            entry.getStyle().set("padding", "0.3rem 0");
            entry.getStyle().set("border-bottom", "1px solid var(--vaadin-border-color)");
            String c = h.getContents() == null || h.getContents().isEmpty()
                    ? "" : h.getContents().get(0);
            entry.add(new Span("v" + h.getVersionNum() + " — "
                    + (h.getState() == null ? "" : h.getState().name())
                    + (h.getLastChanged() == null ? "" : " — " + h.getLastChanged())));
            Paragraph p = new Paragraph(c);
            p.getStyle().set("margin", "0.2rem 0");
            p.getStyle().set("white-space", "pre-wrap");
            entry.add(p);
            d.add(entry);
        }
        return d;
    }

    private Div buildLinkPanel(HTextFlow flow) {
        Div d = new Div();
        d.getStyle().set("margin-top", "0.5rem");
        String href = "/translate/" + projectSlug + "/" + versionSlug + "/"
                + localeStr + "?tf=" + flow.getId();
        Anchor a = new Anchor(href, href);
        d.add(a);
        return d;
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
        long total = 0, translated = 0, approved = 0, needsReview = 0;
        long totalW = 0, translatedW = 0, approvedW = 0, needsReviewW = 0;
        for (HTextFlow tf : flows) {
            long wc = tf.getWordCount() == null ? 0 : tf.getWordCount();
            total++; totalW += wc;
            Optional<HTextFlowTarget> ex =
                    targetRepository.findByTextFlowAndLocale(tf.getId(), currentLocale);
            ContentState s = ex.map(HTextFlowTarget::getState).orElse(ContentState.New);
            switch (s) {
                case Translated -> { translated++; translatedW += wc; }
                case Approved   -> { approved++;   approvedW   += wc; }
                case NeedReview, Rejected -> { needsReview++; needsReviewW += wc; }
                default -> {}
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
