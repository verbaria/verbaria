package org.zanata.spring.vaadin.translate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.KeyModifier;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.theme.lumo.LumoUtility;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;

import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.TextFlowTargetHistoryRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.repository.TextFlowTargetReviewCommentRepository;
import org.zanata.spring.service.EditorPreferencesService;
import org.zanata.spring.service.ai.AiPolicyService;
import org.zanata.spring.service.ai.TranslationProviderRegistry;
import org.zanata.spring.validation.LanguageValidator;
import org.zanata.spring.vaadin.ProgressDialogService;

@SpringComponent
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TranslationRow extends Div {

    private final LanguageValidator languageValidator;
    private final TaskScheduler taskScheduler;
    private final TranslationEditService translationEditService;
    private final TranslationProviderRegistry aiRegistry;
    private final AiPolicyService aiPolicy;
    private final ProgressDialogService progressDialogs;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final TextFlowTargetRepository targetRepository;
    private final AccountRepository accountRepository;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;

    private RowContext ctx;
    private HTextFlow flow;
    private ContentState initialState;
    private String source;
    private String initialContent;
    private boolean isSourceLocale;
    private boolean canEdit;
    private boolean canReview;

    public TranslationRow(LanguageValidator languageValidator,
                          TaskScheduler taskScheduler,
                          TranslationEditService translationEditService,
                          TranslationProviderRegistry aiRegistry,
                          AiPolicyService aiPolicy,
                          ProgressDialogService progressDialogs,
                          TextFlowTargetReviewCommentRepository reviewCommentRepository,
                          TextFlowTargetHistoryRepository historyRepository,
                          TextFlowTargetRepository targetRepository,
                          AccountRepository accountRepository,
                          LocaleRepository localeRepository,
                          LocaleMemberRepository localeMemberRepository) {
        this.languageValidator = languageValidator;
        this.taskScheduler = taskScheduler;
        this.translationEditService = translationEditService;
        this.aiRegistry = aiRegistry;
        this.aiPolicy = aiPolicy;
        this.progressDialogs = progressDialogs;
        this.reviewCommentRepository = reviewCommentRepository;
        this.historyRepository = historyRepository;
        this.targetRepository = targetRepository;
        this.accountRepository = accountRepository;
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
    }

    public TranslationRow populate(RowContext ctx, HTextFlow flow,
                                   Optional<HTextFlowTarget> existing,
                                   ContentState initialState, String source) {
        this.ctx = ctx;
        this.flow = flow;
        this.initialState = initialState;
        this.source = source;
        this.initialContent = existing.map(HTextFlowTarget::getContents)
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .orElse("");
        this.isSourceLocale = ctx.sourceLocale() != null
                && ctx.sourceLocale().getId().equalsIgnoreCase(ctx.localeStr());
        this.canEdit = isAuthenticated();
        this.canReview = canReviewLocale();
        build();
        return this;
    }

    private void build() {
        getStyle().set("border", "1px solid var(--vaadin-border-color)");
        getStyle().set("border-radius", "8px");
        getStyle().set("padding", ctx.prefs().compactRows() ? "0.4rem 0.6rem" : "0.75rem");
        getStyle().set("margin-bottom", ctx.prefs().compactRows() ? "0.4rem" : "0.75rem");
        getStyle().set("box-sizing", "border-box");
        getStyle().set("overflow", "hidden");
        setWidthFull();

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(false);
        row.getStyle().set("box-sizing", "border-box");

        VerticalLayout left = buildLeftColumn();
        VerticalLayout right = buildRightColumn();

        row.add(left, right);
        row.setFlexGrow(1, left);
        row.setFlexGrow(1, right);
        add(row);
    }

    private VerticalLayout buildLeftColumn() {
        VerticalLayout left = new VerticalLayout();
        left.setPadding(false);
        left.setSpacing(false);
        left.setWidth(null);
        left.getStyle().set("flex", "1 1 0");
        left.getStyle().set("min-width", "0");
        left.getStyle().set("border-right", "1px solid var(--vaadin-border-color)");
        left.getStyle().set("padding-right", "1rem");
        left.getStyle().set("margin-right", "1rem");

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

        Button detailsBtn = new Button(getTranslation("translate.action.details"),
                LineAwesomeIcon.INFO_CIRCLE_SOLID.create());
        detailsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Button bookmarkBtn = new Button(LineAwesomeIcon.BOOKMARK_SOLID.create(),
                e -> copyPermalink(flow.getId()));
        bookmarkBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL,
                ButtonVariant.LUMO_ICON);
        String bookmarkTip = getTranslation("translate.action.bookmarkTooltip");
        bookmarkBtn.getElement().setAttribute("title", bookmarkTip);
        bookmarkBtn.getElement().setAttribute("aria-label", bookmarkTip);

        Div detailsPanel = buildDetailsPanel();
        detailsPanel.setVisible(false);
        detailsBtn.addClickListener(e -> detailsPanel.setVisible(!detailsPanel.isVisible()));

        HorizontalLayout leftBtns = new HorizontalLayout(detailsBtn, bookmarkBtn);
        leftBtns.setSpacing(true);
        left.add(resId, srcText, leftBtns, detailsPanel);
        return left;
    }

    private VerticalLayout buildRightColumn() {
        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setWidth(null);
        right.getStyle().set("flex", "1 1 0");
        right.getStyle().set("min-width", "0");

        LocaleId rowLocale = isSourceLocale ? ctx.sourceLocale() : ctx.currentLocale();
        TranslationEditor area = new TranslationEditor(
                languageValidator, taskScheduler, toJavaLocale(rowLocale));
        area.setValue(isSourceLocale ? source : initialContent);
        area.setReadOnly(!canEdit);

        if (canEdit && !isSourceLocale) {
            Shortcuts.addShortcutListener(area, () -> area.setValue(source),
                    Key.KEY_G, KeyModifier.ALT).listenOn(area);
            Shortcuts.addShortcutListener(area, () -> area.setValue(""),
                    Key.KEY_X, KeyModifier.ALT).listenOn(area);
        }

        Span stateSpan = new Span(initialState.name());
        applyStateColor(stateSpan, initialState);

        Button save = buildSaveButton(area, stateSpan);
        if (canEdit) {
            Shortcuts.addShortcutListener(area, save::click,
                    Key.ENTER, KeyModifier.CONTROL).listenOn(area);
            if (!isSourceLocale) {
                Shortcuts.addShortcutListener(area, () -> saveAsFuzzy(area, stateSpan),
                        Key.KEY_S, KeyModifier.CONTROL).listenOn(area)
                        .setBrowserDefaultAllowed(false);
            }
        }

        Button historyBtn = new Button(getTranslation("translate.action.history"),
                LineAwesomeIcon.CLOCK_SOLID.create());
        historyBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Div historyPanel = buildHistoryPanel(flow.getId(), initialContent,
                () -> area.getValue());
        boolean initiallyOpen = ctx.historyExpanded().contains(flow.getId())
                || (ctx.prefs().autoOpenHistory()
                        && !ctx.historyCollapsed().contains(flow.getId()));
        historyPanel.setVisible(initiallyOpen);
        historyBtn.addClickListener(e -> {
            Div fresh = buildHistoryPanel(flow.getId(), initialContent,
                    () -> area.getValue());
            historyPanel.getElement().removeAllChildren();
            fresh.getChildren().forEach(child -> historyPanel.getElement()
                    .appendChild(child.getElement()));
            boolean nowOpen = !historyPanel.isVisible();
            historyPanel.setVisible(nowOpen);
            if (nowOpen) {
                ctx.historyExpanded().add(flow.getId());
                ctx.historyCollapsed().remove(flow.getId());
            } else {
                ctx.historyCollapsed().add(flow.getId());
                ctx.historyExpanded().remove(flow.getId());
            }
        });

        MenuBar aiBtn = buildAiButton(area);
        Button approve = buildApproveButton(stateSpan);
        Button reject = buildRejectButton(stateSpan, historyPanel);

        HorizontalLayout actionRow = new HorizontalLayout(
                save, approve, reject, historyBtn, stateSpan);
        if (aiBtn != null) actionRow.add(aiBtn);
        actionRow.setAlignItems(FlexComponent.Alignment.CENTER);
        actionRow.setSpacing(true);
        actionRow.getStyle().set("margin-top", "0.4rem");
        actionRow.getStyle().set("flex-wrap", "wrap");

        right.add(area, actionRow, historyPanel);
        return right;
    }

    private Button buildSaveButton(TranslationEditor area, Span stateSpan) {
        Button save = new Button(getTranslation("translate.row.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        save.setEnabled(canEdit);
        if (!canEdit) {
            save.setTooltipText(getTranslation("translate.tooltip.signInToEdit"));
        }
        save.addClickListener(e -> {
            if (isSourceLocale) {
                translationEditService.updateSource(flow.getId(), area.getValue());
                Notification.show(getTranslation("translate.row.savedSource"),
                        2000, Notification.Position.BOTTOM_START);
            } else {
                ContentState newState = translationEditService.save(
                        flow.getId(), ctx.currentLocale(), area.getValue());
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Notification.show(getTranslation("translate.row.saved"),
                        2000, Notification.Position.BOTTOM_START);
            }
        });
        return save;
    }

    private void saveAsFuzzy(TranslationEditor area, Span stateSpan) {
        try {
            translationEditService.save(flow.getId(), ctx.currentLocale(), area.getValue());
            ContentState ns = translationEditService.changeState(
                    flow.getId(), ctx.currentLocale(), ContentState.NeedReview);
            stateSpan.setText(ns.name());
            applyStateColor(stateSpan, ns);
            Notification.show(getTranslation("translate.row.savedFuzzy"),
                    2000, Notification.Position.BOTTOM_START);
        } catch (Exception ex) {
            Notification.show(getTranslation("translate.row.saveFuzzyFailed", ex.getMessage()),
                    4000, Notification.Position.MIDDLE);
        }
    }

    private MenuBar buildAiButton(TranslationEditor area) {
        if (isSourceLocale || !aiPolicy.canCurrentUserUseAi()) {
            return null;
        }
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY_INLINE, MenuBarVariant.SMALL);
        var trigger = bar.addItem(LineAwesomeIcon.MAGIC_SOLID.create());
        trigger.getElement().setAttribute("aria-label", getTranslation("translate.action.aiPerRow"));
        trigger.getElement().setAttribute("title", getTranslation("translate.action.aiPerRow"));
        for (var provider : aiRegistry.available()) {
            trigger.getSubMenu().addItem(provider.displayName(), ev -> {
                String ctxStr = flow.getPotEntryData() == null ? null
                        : flow.getPotEntryData().getContext();
                String providerName = provider.displayName();
                progressDialogs.run(getTranslation("ai.translate.bulkRunning", providerName),
                        handle -> {
                            handle.status(handle.t("translate.docSwitcher.callingProvider", providerName));
                            return provider.translate(source, ctx.sourceLocale(),
                                    ctx.currentLocale(), ctxStr);
                        })
                        .whenComplete((out, err) -> {
                            if (err != null) {
                                Notification.show(getTranslation("ai.translate.failed", err.getMessage()),
                                        5000, Notification.Position.MIDDLE);
                                return;
                            }
                            if (out != null && !out.isBlank()) {
                                area.setValue(out);
                                Notification.show(getTranslation("ai.translate.filledBy", providerName),
                                        2000, Notification.Position.BOTTOM_START);
                            }
                        });
            });
        }
        return bar;
    }

    private Button buildApproveButton(Span stateSpan) {
        boolean hasSavedContent = !initialContent.isBlank();
        Button approve = new Button(getTranslation("translate.action.approve"),
                LineAwesomeIcon.CHECK_SOLID.create());
        approve.addThemeVariants(ButtonVariant.SUCCESS, ButtonVariant.SMALL);
        approve.setEnabled(!isSourceLocale && canReview && hasSavedContent);
        if (isSourceLocale) {
            approve.setVisible(false);
        } else if (!canReview) {
            approve.setTooltipText(getTranslation("translate.tooltip.joinTeamForReview",
                    ctx.localeStr(), getTranslation("translate.row.approveTip.review")));
        } else if (!hasSavedContent) {
            approve.setTooltipText(getTranslation("translate.tooltip.saveBeforeReview",
                    getTranslation("translate.row.approveTip.review")));
        }
        approve.addClickListener(e -> {
            try {
                ContentState newState = translationEditService.changeState(
                        flow.getId(), ctx.currentLocale(), ContentState.Approved);
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Notification.show(getTranslation("translate.approve.success"),
                        2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                Notification.show(getTranslation("translate.row.approveFailed", ex.getMessage()),
                        4000, Notification.Position.MIDDLE);
            }
        });
        return approve;
    }

    private Button buildRejectButton(Span stateSpan, Div historyPanel) {
        boolean hasSavedContent = !initialContent.isBlank();
        Button reject = new Button(getTranslation("translate.action.reject"),
                LineAwesomeIcon.TIMES_SOLID.create());
        reject.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);
        reject.setEnabled(!isSourceLocale && canReview && hasSavedContent);
        if (isSourceLocale) {
            reject.setVisible(false);
        } else if (!canReview) {
            reject.setTooltipText(getTranslation("translate.tooltip.joinTeamForReview",
                    ctx.localeStr(), getTranslation("translate.row.rejectTip.review")));
        } else if (!hasSavedContent) {
            reject.setTooltipText(getTranslation("translate.tooltip.saveBeforeReview",
                    getTranslation("translate.row.rejectTip.review")));
        }
        reject.addClickListener(e -> openRejectDialog(stateSpan, historyPanel));
        return reject;
    }

    private Div buildDetailsPanel() {
        Div d = new Div();
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        d.getStyle().set("border-radius", "6px");
        d.getStyle().set("padding", "0.6rem");
        d.getStyle().set("margin-top", "0.5rem");
        d.getStyle().set("font-size", "0.875rem");
        List<String> rows = new ArrayList<>();
        rows.add(label(getTranslation("translate.details.resourceId"), flow.getResId()));
        rows.add(label(getTranslation("translate.details.messageContext"),
                noContent(flow.getPotEntryData() == null ? null
                        : flow.getPotEntryData().getContext())));
        rows.add(label(getTranslation("translate.details.reference"),
                noContent(flow.getPotEntryData() == null ? null
                        : flow.getPotEntryData().getReferences())));
        rows.add(label(getTranslation("translate.details.flags"),
                noContent(flow.getPotEntryData() == null ? null
                        : flow.getPotEntryData().getFlags())));
        rows.add(label(getTranslation("translate.details.sourceComment"),
                noContent(flow.getComment() == null ? null
                        : String.valueOf(flow.getComment()))));
        for (String r : rows) {
            Paragraph p = new Paragraph();
            p.getElement().setProperty("innerHTML", r);
            p.addClassNames(LumoUtility.Margin.NONE);
            d.add(p);
        }
        return d;
    }

    private Div buildHistoryPanel(Long textFlowId, String savedContent,
                                  Supplier<String> liveContent) {
        Div d = new Div();
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        d.getStyle().set("border-radius", "6px");
        d.getStyle().set("padding", "0.6rem");
        d.getStyle().set("margin-top", "0.5rem");
        d.getStyle().set("font-size", "0.875rem");

        List<HTextFlowTargetReviewComment> comments = ctx.prefs().showReviewComments()
                ? reviewCommentRepository.findByTextFlowAndLocale(textFlowId, ctx.currentLocale())
                : List.of();
        if (!comments.isEmpty()) {
            Span header = new Span(getTranslation("translate.history.review"));
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
                Span meta = new Span(getTranslation("translate.history.commentBy",
                        who, c.getTargetVersion(), when));
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

        List<HistoryRow> rows = new ArrayList<>();
        List<HTextFlowTargetHistory> hist =
                historyRepository.findByTextFlowAndLocale(textFlowId, ctx.currentLocale());
        for (HTextFlowTargetHistory h : hist) {
            rows.add(new HistoryRow(
                    "v" + h.getVersionNum(),
                    h.getState() == null ? "" : h.getState().name(),
                    h.getLastModifiedBy() == null ? "" : h.getLastModifiedBy().getName(),
                    h.getLastChanged() == null ? "" : h.getLastChanged().toString(),
                    firstContent(h.getContents())));
        }
        if (savedContent != null && !savedContent.isBlank()) {
            HTextFlowTarget cur = targetRepository
                    .findByTextFlowAndLocaleWithModifier(textFlowId, ctx.currentLocale())
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
        String live = liveContent == null ? null : liveContent.get();
        if (live != null && !live.isEmpty() && !Objects.equals(live, savedContent)) {
            rows.add(new HistoryRow(getTranslation("translate.history.unsavedRow"),
                    "—", getTranslation("translate.history.you"), "—", live));
        }

        if (rows.isEmpty() && comments.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("translate.history.empty.noVersions"));
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            empty.getStyle().set("margin", "0");
            d.add(empty);
            return d;
        }

        Grid<HistoryRow> grid = new Grid<>();
        grid.setItems(rows);
        grid.setAllRowsVisible(true);
        grid.setSelectionMode(Grid.SelectionMode.MULTI);
        grid.addColumn(HistoryRow::version).setHeader(getTranslation("translate.history.col.version"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::state).setHeader(getTranslation("translate.history.col.state"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::modifier).setHeader(getTranslation("translate.history.col.by"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(HistoryRow::when).setHeader(getTranslation("translate.history.col.when"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(r -> {
            String c = r.content() == null ? "" : r.content();
            return c.length() > 120 ? c.substring(0, 117) + "…" : c;
        }).setHeader(getTranslation("translate.history.col.content")).setFlexGrow(1);

        Button compare = new Button(getTranslation("translate.history.compareSelected"),
                LineAwesomeIcon.NOT_EQUAL_SOLID.create());
        compare.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        compare.setEnabled(false);
        Span hint = new Span(getTranslation("translate.history.selectExactlyTwo"));
        hint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        hint.getStyle().set("font-size", "0.8rem");
        hint.getStyle().set("margin-left", "0.5rem");
        grid.asMultiSelect().addSelectionListener(ev -> {
            int n = ev.getValue().size();
            compare.setEnabled(n == 2);
            hint.setText(n == 2 ? getTranslation("translate.history.clickToCompare")
                    : getTranslation("translate.history.selectionCount", n));
        });
        compare.addClickListener(ev -> {
            var sel = new ArrayList<>(grid.getSelectedItems());
            if (sel.size() != 2) return;
            openDiffDialog(sel.get(0), sel.get(1));
        });

        HorizontalLayout toolbar = new HorizontalLayout(compare, hint);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("margin", "0.25rem 0");
        d.add(toolbar, grid);
        return d;
    }

    private void openRejectDialog(Span stateSpan, Div historyPanel) {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("translate.reject.title"));
        dlg.setWidth("480px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(false);

        Paragraph intro = new Paragraph(getTranslation("translate.reject.intro"));
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        intro.getStyle().set("margin", "0 0 0.75rem 0");
        intro.getStyle().set("font-size", "0.9rem");

        TextArea reason = new TextArea(getTranslation("translate.reject.reasonLabel"));
        reason.setWidthFull();
        reason.setMinHeight("6rem");
        reason.setRequired(true);
        reason.setValueChangeMode(ValueChangeMode.EAGER);

        Button confirm = new Button(getTranslation("translate.reject.confirm"));
        confirm.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.ERROR);
        confirm.setEnabled(false);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        cancel.addThemeVariants(ButtonVariant.TERTIARY);

        reason.addValueChangeListener(e -> confirm.setEnabled(
                e.getValue() != null && !e.getValue().trim().isEmpty()));

        confirm.addClickListener(e -> {
            String text = reason.getValue() == null ? "" : reason.getValue().trim();
            if (text.isEmpty()) return;
            String reviewer = currentUsername();
            try {
                ContentState newState = translationEditService.changeState(
                        flow.getId(), ctx.currentLocale(), ContentState.Rejected,
                        text, reviewer);
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                Div fresh = buildHistoryPanel(flow.getId(), null, () -> null);
                historyPanel.getElement().removeAllChildren();
                fresh.getChildren().forEach(child -> historyPanel.getElement()
                        .appendChild(child.getElement()));
                Notification.show(getTranslation("translate.reject.success"),
                        2000, Notification.Position.BOTTOM_START);
                dlg.close();
            } catch (Exception ex) {
                Notification.show(getTranslation("translate.row.rejectFailed", ex.getMessage()),
                        4000, Notification.Position.MIDDLE);
            }
        });

        dlg.add(intro, reason);
        dlg.getFooter().add(cancel, confirm);
        dlg.open();
        reason.focus();
    }

    private void openDiffDialog(HistoryRow a, HistoryRow b) {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("translate.diff.title", a.version(), b.version()));
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
        Button close = new Button(getTranslation("progress.close"), e -> dlg.close());
        close.addThemeVariants(ButtonVariant.TERTIARY);
        dlg.getFooter().add(close);
        dlg.open();
    }

    private void copyPermalink(Long textFlowId) {
        String href = "/translate/" + ctx.projectSlug() + "/" + ctx.versionSlug() + "/"
                + ctx.localeStr() + "?doc=" + ctx.currentDocId() + "&tf=" + textFlowId;
        UI.getCurrent().getPage().executeJs(
                "const u = new URL($0, location.origin).toString();"
                + "navigator.clipboard.writeText(u);", href);
        Notification.show(getTranslation("translate.permalink.copied"),
                1800, Notification.Position.BOTTOM_START);
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
        var locale = localeRepository.findByLocaleId(ctx.currentLocale()).orElse(null);
        if (locale == null) return false;
        return localeMemberRepository.findByLocaleAndPerson(locale, account.getPerson())
                .map(m -> m.isReviewer() || m.isCoordinator())
                .orElse(false);
    }

    private static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return null;
        return auth.getName();
    }

    static void applyStateColor(Span span, ContentState state) {
        String color;
        switch (state) {
            case Translated -> color = "green";
            case Approved -> color = "darkgreen";
            case NeedReview -> color = "orange";
            case Rejected -> color = "crimson";
            case New -> color = "var(--vaadin-text-color-secondary)";
            default -> color = "var(--vaadin-text-color-secondary)";
        }
        span.getStyle().set("color", color);
        span.getStyle().set("font-weight", "600");
    }

    private String label(String k, String v) {
        return "<strong>" + k + "</strong> "
                + (v == null || v.isBlank()
                    ? "<span style=\"color: var(--vaadin-text-color-secondary)\">"
                            + escapeHtml(getTranslation("translate.details.noContent"))
                            + "</span>"
                    : escapeHtml(v));
    }

    private static String noContent(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static String firstContent(List<String> contents) {
        if (contents == null || contents.isEmpty()) return "";
        String c = contents.get(0);
        return c == null ? "" : c;
    }

    private static Locale toJavaLocale(LocaleId localeId) {
        if (localeId == null || localeId.getId() == null) return null;
        return Locale.forLanguageTag(localeId.getId());
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
                if (leftSide) appendMarked(out, aw[i], color, bg);
                i++;
            } else {
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

    private record HistoryRow(String version, String state, String modifier,
                              String when, String content) {}

    public record RowContext(
            EditorPreferencesService.Prefs prefs,
            LocaleId currentLocale,
            LocaleId sourceLocale,
            String localeStr,
            String projectSlug,
            String versionSlug,
            String currentDocId,
            Set<Long> historyExpanded,
            Set<Long> historyCollapsed) {
    }
}
