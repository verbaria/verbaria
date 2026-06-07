package org.verbaria.server.ui.vaadin.translate;

import org.verbaria.server.headless.security.Roles;
import org.verbaria.server.headless.service.AiTranslationService;
import org.verbaria.server.headless.service.TranslationEditService;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import com.vaadin.flow.component.Component;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.textfield.TextFieldVariant;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;

import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.MessageEvaluateType;
import org.verbaria.server.headless.message.MessageEvaluator;
import org.verbaria.server.headless.message.MessageEvaluators;
import org.verbaria.server.headless.message.MessageInfo;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.rest.dto.TranslationSourceType;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleMemberRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.repository.TextFlowTargetReviewCommentRepository;
import org.verbaria.server.ui.vaadin.service.EditorPreferencesService;
import org.verbaria.server.headless.service.ai.AiPolicyService;
import org.verbaria.server.headless.service.ai.TranslationProviderRegistry;
import org.verbaria.server.headless.validation.CompositeLanguageValidator;
import org.verbaria.server.headless.validation.LanguageValidator;
import org.verbaria.server.headless.validation.MessageFormatValidator;
import org.verbaria.server.ui.vaadin.ProgressDialogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SpringComponent
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TranslationRow extends Div {

    private static final Logger log = LoggerFactory.getLogger(TranslationRow.class);

    private final LanguageValidator languageValidator;
    private final TaskScheduler taskScheduler;
    private final TranslationEditService translationEditService;
    private final AiTranslationService aiTranslationService;
    private final TranslationProviderRegistry aiRegistry;
    private final AiPolicyService aiPolicy;
    private final ProgressDialogService progressDialogs;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final TextFlowTargetRepository targetRepository;
    private final AccountRepository accountRepository;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final MessageEvaluators messageEvaluators;

    private RowContext ctx;
    private HTextFlow flow;
    private ContentState initialState;
    private String source;
    private String initialContent;
    /** Source revision this translation was made against (null if none). */
    private Integer initialTargetRevision;
    /** Last persisted editor content; Save is enabled only when it differs. */
    private String savedContent = "";
    /** Current editor text, kept fresh from value-change events. */
    private String liveContent = "";
    /** The editor component, so save actions can read the blur-synced value. */
    private TranslationEditor editorArea;
    /** Live persisted state, updated on every save/approve/reject. */
    private ContentState currentState;
    private Button saveButton;
    private Button approveButton;
    private Button rejectButton;
    private boolean isSourceLocale;
    private boolean canEdit;
    private boolean canReview;
    private boolean detailsMode;

    public TranslationRow(LanguageValidator languageValidator,
                          TaskScheduler taskScheduler,
                          TranslationEditService translationEditService,
                          AiTranslationService aiTranslationService,
                          TranslationProviderRegistry aiRegistry,
                          AiPolicyService aiPolicy,
                          ProgressDialogService progressDialogs,
                          TextFlowTargetReviewCommentRepository reviewCommentRepository,
                          TextFlowTargetHistoryRepository historyRepository,
                          TextFlowTargetRepository targetRepository,
                          AccountRepository accountRepository,
                          LocaleRepository localeRepository,
                          LocaleMemberRepository localeMemberRepository,
                          MessageEvaluators messageEvaluators) {
        this.languageValidator = languageValidator;
        this.messageEvaluators = messageEvaluators;
        this.taskScheduler = taskScheduler;
        this.translationEditService = translationEditService;
        this.aiTranslationService = aiTranslationService;
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
                                   ContentState initialState, String source,
                                   boolean detailsMode) {
        this.ctx = ctx;
        this.detailsMode = detailsMode;
        this.flow = flow;
        this.initialState = initialState;
        this.currentState = initialState;
        this.source = source;
        this.initialContent = existing.map(HTextFlowTarget::getContents)
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0))
                .orElse("");
        this.initialTargetRevision = existing
                .map(HTextFlowTarget::getTextFlowRevision).orElse(null);
        this.isSourceLocale = ctx.sourceLocale() != null
                && ctx.sourceLocale().getId().equalsIgnoreCase(ctx.localeStr());
        this.canEdit = isAuthenticated();
        this.canReview = canReviewLocale();
        build();
        return this;
    }

    private void build() {
        boolean compact = ctx.prefs().compactRows();
        if (detailsMode) {
            addClassNames(AuraUtility.Padding.SMALL,
                    AuraUtility.BoxSizing.BORDER, AuraUtility.Overflow.HIDDEN);
        } else {
            addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT,
                    AuraUtility.BorderRadius.MEDIUM,
                    compact ? AuraUtility.Padding.SMALL : AuraUtility.Padding.MEDIUM,
                    compact ? AuraUtility.Margin.Bottom.SMALL : AuraUtility.Margin.Bottom.MEDIUM,
                    AuraUtility.BoxSizing.BORDER,
                    AuraUtility.Overflow.HIDDEN);
        }
        setWidthFull();

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setSpacing(false);
        row.addClassNames(AuraUtility.BoxSizing.BORDER);

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
        left.addClassNames(AuraUtility.Flex.ONE,
                AuraUtility.Border.RIGHT, AuraUtility.BorderColor.DEFAULT,
                AuraUtility.Padding.Right.LARGE, AuraUtility.Margin.Right.LARGE, AuraUtility.MinWidth.NONE);

        String context = translationEditService.gettextContext(flow);
        String displayKey = context != null && !context.isEmpty()
                ? context
                : flow.getResId();
        Span resId = new Span(displayKey);
        resId.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.TextColor.SECONDARY);
        Paragraph srcText = new Paragraph(source);
        srcText.addClassNames(AuraUtility.Margin.NONE, AuraUtility.Whitespace.PRE_WRAP);

        Button detailsBtn = new Button(getTranslation("translate.action.details"),
                LineAwesomeIcon.INFO_CIRCLE_SOLID.create());
        detailsBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        Button bookmarkBtn = new Button(LineAwesomeIcon.BOOKMARK_SOLID.create(),
                e -> copyPermalink(flow.getId()));
        bookmarkBtn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        String bookmarkTip = getTranslation("translate.action.bookmarkTooltip");
        bookmarkBtn.getElement().setAttribute("title", bookmarkTip);
        bookmarkBtn.getElement().setAttribute("aria-label", bookmarkTip);

        Div detailsPanel = buildDetailsPanel();
        detailsPanel.setVisible(false);
        detailsBtn.addClickListener(e -> detailsPanel.setVisible(!detailsPanel.isVisible()));

        HorizontalLayout leftBtns = new HorizontalLayout(detailsBtn, bookmarkBtn);
        leftBtns.setSpacing(true);
        if (!detailsMode) {
            left.add(resId, srcText);
        }
        // Source comment is per-key (source) metadata: a toggle icon sits in the
        // action line with details/bookmark and reveals the comment block below.
        Component commentArea = buildSourceComment(leftBtns);
        left.add(leftBtns);
        if (commentArea != null) {
            left.add(commentArea);
        }
        left.add(detailsPanel);
        return left;
    }

    private VerticalLayout buildRightColumn() {
        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setWidth(null);
        right.addClassNames(AuraUtility.Flex.ONE, AuraUtility.MinWidth.NONE);

        LocaleId rowLocale = isSourceLocale ? ctx.sourceLocale() : ctx.currentLocale();
        // Natural-language checks (warnings) plus, when the project uses a
        // message format, a syntax check (errors) — composed into one validator.
        LanguageValidator validator = languageValidator;
        MessageEvaluator evaluator = rowMessageEvaluator();
        if (evaluator != null) {
            validator = new CompositeLanguageValidator(List.of(
                    languageValidator, new MessageFormatValidator(evaluator)));
        }
        TranslationEditor area = new TranslationEditor(
                validator, taskScheduler, toJavaLocale(rowLocale));
        this.editorArea = area;
        area.setValue(isSourceLocale ? source : initialContent);
        area.setReadOnly(!canEdit);
        // Consulo raw sub-file: highlight by its extension.
        area.setModeForFileExtension(
                translationEditService.consuloContentType(flow));
        liveContent = isSourceLocale ? source : initialContent;
        if (liveContent == null) liveContent = "";
        // Single point that keeps the live text fresh (the AceChanged event
        // carries it; the editor's getValue() only syncs on blur) and refreshes
        // every action button from it.
        area.addAceChangedListener(ev -> {
            liveContent = ev.getValue() == null ? "" : ev.getValue();
            refreshActions();
        });

        Component extField = buildConsuloExtensionField(area);

        if (canEdit && !isSourceLocale) {
            Shortcuts.addShortcutListener(area, () -> area.setValue(source),
                    Key.KEY_G, KeyModifier.ALT).listenOn(area);
            Shortcuts.addShortcutListener(area, () -> area.setValue(""),
                    Key.KEY_X, KeyModifier.ALT).listenOn(area);
        }

        Span stateSpan = new Span(initialState.name());
        applyStateColor(stateSpan, initialState);

        Button save = buildSaveButton(stateSpan);
        if (canEdit) {
            Shortcuts.addShortcutListener(area, save::click,
                    Key.ENTER, KeyModifier.CONTROL).listenOn(area);
            if (!isSourceLocale) {
                Shortcuts.addShortcutListener(area, () -> saveAsFuzzy(stateSpan),
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

        MenuBar aiBtn = buildAiButton(area, stateSpan);
        Button approve = buildApproveButton(stateSpan);
        Button reject = buildRejectButton(stateSpan, historyPanel);
        refreshActions();

        Button evaluateBtn = buildEvaluateButton(area);
        Component reviewWarn = buildNeedsReviewBadge();

        HorizontalLayout actionRow = new HorizontalLayout(
                save, approve, reject, historyBtn, stateSpan);
        if (aiBtn != null) actionRow.add(aiBtn);
        if (evaluateBtn != null) actionRow.add(evaluateBtn);
        if (reviewWarn != null) actionRow.add(reviewWarn);
        actionRow.setAlignItems(FlexComponent.Alignment.CENTER);
        actionRow.setSpacing(true);
        actionRow.addClassNames(AuraUtility.Margin.Top.SMALL, AuraUtility.FlexWrap.WRAP);

        if (extField != null) right.add(extField);
        right.add(area, actionRow, historyPanel);
        return right;
    }

    /**
     * The message evaluator that applies to this row, or {@code null} when it
     * doesn't: the project isn't configured for message formats, or this is a
     * consulo raw sub-file (a whole file, not a message string).
     */
    private MessageEvaluator rowMessageEvaluator() {
        if (translationEditService.isConsuloFile(flow)) {
            return null;
        }
        return messageEvaluators.forType(ctx.messageEvaluateType());
    }

    private Button buildEvaluateButton(TranslationEditor area) {
        if (rowMessageEvaluator() == null) {
            return null;
        }
        Button btn = new Button(getTranslation("translate.action.evaluate"));
        btn.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        btn.addClickListener(e -> openEvaluateDialog(area));
        return btn;
    }

    /**
     * Preview a message-format string: parse the current editor value, surface
     * a syntax error if invalid, otherwise show an input field per argument and
     * render the formatted result on "Evaluate".
     */
    private void openEvaluateDialog(TranslationEditor area) {
        MessageEvaluator evaluator = rowMessageEvaluator();
        if (evaluator == null) {
            return;
        }
        String pattern = area.getValue() == null ? "" : area.getValue();
        MessageInfo info = evaluator.analyze(pattern, toJavaLocale(ctx.currentLocale()));

        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("translate.evaluate.title"));
        dlg.setWidth("440px");
        dlg.setCloseOnEsc(true);
        dlg.setCloseOnOutsideClick(true);

        VerticalLayout body = new VerticalLayout();
        body.setPadding(false);
        body.setSpacing(true);

        if (!info.isValid()) {
            Paragraph err = new Paragraph(getTranslation(
                    "translate.evaluate.invalid", info.getError()));
            err.addClassNames(AuraUtility.TextColor.ERROR);
            body.add(err);
        } else {
            List<TextField> fields = new ArrayList<>();
            for (String label : info.getArgumentLabels()) {
                TextField f = new TextField(label);
                f.setWidthFull();
                fields.add(f);
                body.add(f);
            }
            Span result = new Span();
            result.addClassNames(AuraUtility.Whitespace.PRE_WRAP,
                    AuraUtility.FontWeight.SEMIBOLD);
            Runnable run = () -> {
                List<String> inputs = new ArrayList<>(fields.size());
                for (TextField f : fields) {
                    inputs.add(f.getValue() == null ? "" : f.getValue());
                }
                try {
                    result.getElement().setProperty("innerText",
                            info.format(inputs));
                } catch (RuntimeException ex) {
                    result.getElement().setProperty("innerText", getTranslation(
                            "translate.evaluate.error", ex.getMessage()));
                }
            };
            Button evalBtn = new Button(getTranslation("translate.evaluate.run"),
                    e -> run.run());
            evalBtn.addThemeVariants(ButtonVariant.PRIMARY);
            run.run(); // initial preview (handles zero-argument messages)
            Span resultLabel =
                    new Span(getTranslation("translate.evaluate.result"));
            resultLabel.addClassNames(AuraUtility.TextColor.SECONDARY);
            body.add(evalBtn, resultLabel, result);
        }
        dlg.add(body);
        Button close = new Button(getTranslation("common.close"),
                e -> dlg.close());
        dlg.getFooter().add(close);
        dlg.open();
    }

    /**
     * For a consulo raw sub-file, an editable "Extension" field. Reviewers may
     * change it to any value; the change is persisted and immediately re-applies
     * the editor's syntax highlighting. Returns {@code null} when the flow is
     * not a consulo sub-file (an ordinary {@code key: text} entry).
     */
    private Component buildConsuloExtensionField(TranslationEditor area) {
        if (!translationEditService.isConsuloFile(flow)) {
            return null;
        }
        String ext = translationEditService.consuloExtension(flow);
        TextField field = new TextField(getTranslation("translate.row.extension"));
        field.setValue(ext == null ? "" : ext);
        field.addThemeVariants(TextFieldVariant.SMALL);
        field.setWidth("10rem");
        field.setReadOnly(!canReview);
        if (!canReview) {
            field.setHelperText(
                    getTranslation("translate.row.extensionReviewerOnly"));
        }
        // Listener added AFTER setValue so the initial value doesn't persist.
        field.addValueChangeListener(e -> {
            String val = e.getValue() == null ? "" : e.getValue().trim();
            if (val.startsWith(".")) val = val.substring(1);
            try {
                translationEditService.updateConsuloFileExt(flow.getId(), val);
                area.setModeForFileExtension(val);
                Notification.show(getTranslation("translate.row.extensionSaved"),
                        2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                log.error("Failed to save consulo extension for textFlow {}",
                        flow.getId(), ex);
                Notification.show(getTranslation(
                        "translate.row.extensionSaveFailed", ex.getMessage()),
                        4000, Notification.Position.MIDDLE);
            }
        });
        return field;
    }

    /** True when this translation predates the current source revision. */
    private boolean needsReview() {
        if (isSourceLocale || initialTargetRevision == null
                || flow.getRevision() == null) {
            return false;
        }
        boolean done = initialState == ContentState.Translated
                || initialState == ContentState.Approved;
        return done && initialTargetRevision < flow.getRevision();
    }

    /**
     * Warning + one-click Review when the source changed after this translation
     * was made. Review re-stamps the target to the current source revision
     * (content/state untouched), clearing the warning. {@code null} when fresh.
     */
    private Component buildNeedsReviewBadge() {
        if (!needsReview()) {
            return null;
        }
        Span warn = new Span(getTranslation("translate.row.needsReview"));
        warn.getElement().setAttribute("title",
                getTranslation("translate.row.needsReviewTip"));
        warn.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.TextColor.ORANGE);

        HorizontalLayout box = new HorizontalLayout(warn);
        box.setAlignItems(FlexComponent.Alignment.CENTER);
        box.setSpacing(true);

        if (canReview) {
            Button review = new Button(getTranslation("translate.row.review"));
            review.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            review.addClickListener(e -> {
                try {
                    translationEditService.markReviewed(
                            flow.getId(), ctx.currentLocale());
                    initialTargetRevision = flow.getRevision();
                    box.setVisible(false);
                    Notification.show(
                            getTranslation("translate.row.reviewDone"),
                            2000, Notification.Position.BOTTOM_START);
                } catch (Exception ex) {
                    log.error("Failed to mark reviewed for textFlow {}",
                            flow.getId(), ex);
                    Notification.show(getTranslation(
                            "translate.row.reviewFailed", ex.getMessage()),
                            4000, Notification.Position.MIDDLE);
                }
            });
            box.add(review);
        }
        return box;
    }

    /**
     * Builds the source-comment block (shown only when a comment exists) and
     * adds a toggle icon to {@code leftBtns} that reveals/hides it. The block
     * saves only when its check (Accept) suffix is clicked — never on edit.
     * Returns {@code null} for non-reviewers.
     */
    private Component buildSourceComment(HorizontalLayout leftBtns) {
        if (!canReview) {
            return null;
        }
        String existing = translationEditService.sourceComment(flow);
        boolean hasComment = existing != null && !existing.isEmpty();

        TextArea area = new TextArea(getTranslation("translate.row.sourceComment"));
        area.setValue(existing == null ? "" : existing);
        area.setWidthFull();
        area.addClassNames(AuraUtility.Margin.Top.SMALL);

        Button accept = new Button(LineAwesomeIcon.CHECK_SOLID.create());
        accept.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        String acceptTip = getTranslation("translate.row.sourceCommentAccept");
        accept.getElement().setAttribute("title", acceptTip);
        accept.getElement().setAttribute("aria-label", acceptTip);
        accept.addClickListener(e -> {
            String val = area.getValue() == null ? "" : area.getValue().trim();
            try {
                translationEditService.updateSourceComment(flow.getId(), val);
                Notification.show(
                        getTranslation("translate.row.sourceCommentSaved"),
                        2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                log.error("Failed to save source comment for textFlow {}",
                        flow.getId(), ex);
                Notification.show(getTranslation(
                        "translate.row.sourceCommentSaveFailed", ex.getMessage()),
                        4000, Notification.Position.MIDDLE);
            }
        });
        area.setSuffixComponent(accept);
        area.setVisible(hasComment);

        Button toggle = new Button(LineAwesomeIcon.COMMENT_SOLID.create());
        toggle.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
        String toggleTip = getTranslation("translate.row.sourceCommentAdd");
        toggle.getElement().setAttribute("title", toggleTip);
        toggle.getElement().setAttribute("aria-label", toggleTip);
        toggle.addClickListener(e -> {
            area.setVisible(!area.isVisible());
            if (area.isVisible()) {
                area.focus();
            }
        });
        leftBtns.add(toggle);

        return area;
    }

    private Button buildSaveButton(Span stateSpan) {
        Button save = new Button(getTranslation("translate.row.save"));
        save.addThemeVariants(ButtonVariant.PRIMARY, ButtonVariant.SMALL);
        this.saveButton = save;
        savedContent = (isSourceLocale ? source : initialContent);
        if (savedContent == null) savedContent = "";
        if (!canEdit) {
            save.setTooltipText(getTranslation("translate.tooltip.signInToEdit"));
        }
        save.addClickListener(e -> {
            // Clicking blurs the editor, so getValue() is authoritative here —
            // use it (not the possibly-lagging AceChanged-tracked liveContent)
            // so an edit isn't dropped by updateSource's no-op-on-unchanged guard.
            liveContent = editorArea.getValue() == null ? "" : editorArea.getValue();
            if (isSourceLocale) {
                translationEditService.updateSource(flow.getId(), liveContent,
                        currentUsername());
                Notification.show(getTranslation("translate.row.savedSource"),
                        2000, Notification.Position.BOTTOM_START);
            } else {
                ContentState newState = translationEditService.save(
                        flow.getId(), ctx.currentLocale(), liveContent,
                        currentUsername());
                stateSpan.setText(newState.name());
                applyStateColor(stateSpan, newState);
                currentState = newState;
                Notification.show(getTranslation("translate.row.saved"),
                        2000, Notification.Position.BOTTOM_START);
            }
            savedContent = liveContent;
            refreshActions();
            ctx.refreshRows().run();
        });
        return save;
    }

    /**
     * Single source of truth for action-button enablement, recomputed on every
     * value change and after every save/approve/reject. Save tracks the live
     * editor buffer; Approve/Reject act on the persisted translation, so they
     * key off the saved state only (not pending keystrokes).
     */
    private void refreshActions() {
        boolean dirty = !liveContent.equals(savedContent);
        boolean hasSaved = savedContent != null && !savedContent.isBlank();
        if (saveButton != null) {
            saveButton.setEnabled(canEdit && dirty);
        }
        if (approveButton != null) {
            approveButton.setEnabled(canReview && hasSaved
                    && currentState != ContentState.Approved);
        }
        if (rejectButton != null) {
            rejectButton.setEnabled(canReview && hasSaved
                    && currentState != ContentState.Rejected);
        }
    }

    private void saveAsFuzzy(Span stateSpan) {
        try {
            liveContent = editorArea.getValue() == null ? "" : editorArea.getValue();
            translationEditService.save(flow.getId(), ctx.currentLocale(),
                    liveContent, currentUsername());
            ContentState ns = translationEditService.changeState(
                    flow.getId(), ctx.currentLocale(), ContentState.NeedReview);
            stateSpan.setText(ns.name());
            applyStateColor(stateSpan, ns);
            currentState = ns;
            savedContent = liveContent;
            refreshActions();
            ctx.refreshRows().run();
            Notification.show(getTranslation("translate.row.savedFuzzy"),
                    2000, Notification.Position.BOTTOM_START);
        } catch (Exception ex) {
            log.error("Fuzzy save failed for textFlow {} locale {}",
                    flow.getId(), ctx.currentLocale(), ex);
            Notification.show(getTranslation("translate.row.saveFuzzyFailed", ex.getMessage()),
                    4000, Notification.Position.MIDDLE);
        }
    }

    private MenuBar buildAiButton(TranslationEditor area, Span stateSpan) {
        if (isSourceLocale || !aiPolicy.canCurrentUserUseAi()) {
            return null;
        }
        MenuBar bar = new MenuBar();
        bar.addThemeVariants(MenuBarVariant.TERTIARY, MenuBarVariant.SMALL);
        var trigger = bar.addItem(LineAwesomeIcon.MAGIC_SOLID.create());
        trigger.getElement().setAttribute("aria-label", getTranslation("translate.action.aiPerRow"));
        trigger.getElement().setAttribute("title", getTranslation("translate.action.aiPerRow"));
        for (var provider : aiRegistry.available()) {
            trigger.getSubMenu().addItem(provider.displayName(), ev -> {
                String providerId = provider.id();
                String providerName = provider.displayName();
                String editor = currentUsername();
                progressDialogs.run(getTranslation("ai.translate.bulkRunning", providerName),
                        handle -> {
                            handle.status(handle.t("translate.docSwitcher.callingProvider", providerName));
                            // The service translates AND persists: it saves the
                            // result and, for a reviewer/admin, also approves it,
                            // so there's no second click.
                            return aiTranslationService.translateOne(
                                    flow.getId(), ctx.currentLocale(), providerId, editor);
                        })
                        .whenComplete((res, err) -> {
                            if (err != null) {
                                Notification.show(getTranslation("ai.translate.failed", err.getMessage()),
                                        5000, Notification.Position.MIDDLE);
                                return;
                            }
                            if (res != null && res.content() != null && !res.content().isBlank()) {
                                area.setValue(res.content());
                                liveContent = res.content();
                                if (res.applied()) {
                                    // Auto-saved (and approved for reviewers).
                                    savedContent = res.content();
                                    if (res.state() != null) {
                                        stateSpan.setText(res.state().name());
                                        applyStateColor(stateSpan, res.state());
                                        currentState = res.state();
                                    }
                                    refreshActions();
                                    Notification.show(getTranslation("ai.translate.filledBy", providerName),
                                            2000, Notification.Position.BOTTOM_START);
                                } else {
                                    // A translation already existed: only fill the
                                    // editor; the user must Save/Approve by hand.
                                    refreshActions();
                                    Notification.show(getTranslation("ai.translate.suggested", providerName),
                                            3000, Notification.Position.BOTTOM_START);
                                }
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
        this.approveButton = approve;
        if (!canReview) {
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
                currentState = newState;
                refreshActions();
                ctx.refreshRows().run();
                Notification.show(getTranslation("translate.approve.success"),
                        2000, Notification.Position.BOTTOM_START);
            } catch (Exception ex) {
                log.error("Approve failed for textFlow {} locale {}",
                        flow.getId(), ctx.currentLocale(), ex);
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
        this.rejectButton = reject;
        if (!canReview) {
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
        d.addClassNames(AuraUtility.Background.BASE, AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.SMALL, AuraUtility.Margin.Top.SMALL, AuraUtility.FontSize.SMALL);
        List<String> rows = new ArrayList<>();
        rows.add(label(getTranslation("translate.details.resourceId"), flow.getResId()));
        rows.add(label(getTranslation("translate.details.messageContext"),
                noContent(translationEditService.gettextContext(flow))));
        rows.add(label(getTranslation("translate.details.reference"),
                noContent(translationEditService.gettextReferences(flow))));
        rows.add(label(getTranslation("translate.details.flags"),
                noContent(translationEditService.gettextFlags(flow))));
        rows.add(label(getTranslation("translate.details.sourceComment"),
                noContent(translationEditService.sourceComment(flow))));
        for (String r : rows) {
            Paragraph p = new Paragraph();
            p.getElement().setProperty("innerHTML", r);
            p.addClassNames(AuraUtility.Margin.NONE);
            d.add(p);
        }
        return d;
    }

    private Div buildHistoryPanel(Long textFlowId, String savedContent,
                                  Supplier<String> liveContent) {
        Div d = new Div();
        d.addClassNames(AuraUtility.Background.BASE, AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.SMALL, AuraUtility.Margin.Top.SMALL, AuraUtility.FontSize.SMALL);

        List<HTextFlowTargetReviewComment> comments = ctx.prefs().showReviewComments()
                ? reviewCommentRepository.findByTextFlowAndLocale(textFlowId, ctx.currentLocale())
                : List.of();
        if (!comments.isEmpty()) {
            Span header = new Span(getTranslation("translate.history.review"));
            header.addClassNames(AuraUtility.FontWeight.SEMIBOLD, AuraUtility.Display.BLOCK, AuraUtility.Margin.Bottom.XSMALL);
            d.add(header);
            for (HTextFlowTargetReviewComment c : comments) {
                Div entry = new Div();
                entry.addClassNames(AuraUtility.Padding.Vertical.XSMALL,
                        AuraUtility.Padding.Left.SMALL,
                        AuraUtility.Border.LEFT,
                        AuraUtility.Margin.Vertical.XSMALL);
                // 3px accent (utility border-left is 1px). Theme-token color
                // via the standard --aura-utility-border-color extension point.
                entry.getStyle().set("--aura-utility-border-color", "var(--aura-red-text)");
                entry.getStyle().set("border-left-width", "3px");
                String who = c.getCommenterName() == null ? "" : c.getCommenterName();
                String when = c.getCreationDate() == null ? "" : " — " + c.getCreationDate();
                Span meta = new Span(getTranslation("translate.history.commentBy",
                        who, c.getTargetVersion(), when));
                meta.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);
                Paragraph p = new Paragraph(c.getCommentText());
                p.getStyle().set("margin", "0.15rem 0 0 0");
                p.addClassNames(AuraUtility.Whitespace.PRE_WRAP);
                entry.add(meta, p);
                d.add(entry);
            }
            Div sep = new Div();
            sep.getStyle().set("height", "1px");
            sep.getStyle().set("background", "var(--vaadin-border-color)");
            sep.addClassNames(AuraUtility.Margin.Vertical.SMALL);
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
                    sourceLabel(h.getSourceType()),
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
                        sourceLabel(cur.getSourceType()),
                        firstContent(cur.getContents())));
            }
        }
        String live = liveContent == null ? null : liveContent.get();
        if (live != null && !live.isEmpty() && !Objects.equals(live, savedContent)) {
            rows.add(new HistoryRow(getTranslation("translate.history.unsavedRow"),
                    "—", getTranslation("translate.history.you"), "—", "", live));
        }

        if (rows.isEmpty() && comments.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("translate.history.empty.noVersions"));
            empty.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.NONE);
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
        grid.addColumn(HistoryRow::source).setHeader(getTranslation("translate.history.col.source"))
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
        hint.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL,
                AuraUtility.Margin.Left.SMALL);
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
        toolbar.addClassNames(AuraUtility.Margin.Vertical.XSMALL);
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
        intro.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Bottom.MEDIUM, AuraUtility.FontSize.SMALL);

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
                currentState = newState;
                refreshActions();
                ctx.refreshRows().run();
                Div fresh = buildHistoryPanel(flow.getId(), null, () -> null);
                historyPanel.getElement().removeAllChildren();
                fresh.getChildren().forEach(child -> historyPanel.getElement()
                        .appendChild(child.getElement()));
                Notification.show(getTranslation("translate.reject.success"),
                        2000, Notification.Position.BOTTOM_START);
                dlg.close();
            } catch (Exception ex) {
                log.error("Reject failed for textFlow {} locale {}",
                        flow.getId(), ctx.currentLocale(), ex);
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
        left.addClassNames(AuraUtility.Flex.ONE);
        right.addClassNames(AuraUtility.Flex.ONE);
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
        if (Roles.isAdmin(auth)) return true;
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) return false;
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
        // Approved and Translated both render as success-green (the legacy
        // "darkgreen" / "green" distinction is dropped — text-success already
        // uses the theme's contrast-tuned dark green token).
        String colorClass = switch (state) {
            case Translated, Approved -> AuraUtility.TextColor.SUCCESS;
            case NeedReview -> AuraUtility.TextColor.ORANGE;
            case Rejected -> AuraUtility.TextColor.ERROR;
            case New -> AuraUtility.TextColor.SECONDARY;
            default -> AuraUtility.TextColor.SECONDARY;
        };
        span.addClassNames(colorClass, AuraUtility.FontWeight.SEMIBOLD);
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
        cell.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.SMALL, AuraUtility.Background.BASE, AuraUtility.Overflow.AUTO);
        Span header = new Span(version + " — " + state + (when.isEmpty() ? "" : " — " + when));
        header.addClassNames(AuraUtility.FontWeight.SEMIBOLD, AuraUtility.Display.BLOCK, AuraUtility.Margin.Bottom.SMALL);
        Div content = new Div();
        content.addClassNames(AuraUtility.Whitespace.PRE_WRAP);
        content.getStyle().set("font-family", "monospace");
        content.addClassNames(AuraUtility.FontSize.SMALL);
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
                              String when, String source, String content) {}

    private static String sourceLabel(TranslationSourceType type) {
        return type == TranslationSourceType.MACHINE_TRANS ? "AI" : "";
    }

    public record RowContext(
            EditorPreferencesService.Prefs prefs,
            LocaleId currentLocale,
            LocaleId sourceLocale,
            String localeStr,
            String projectSlug,
            String versionSlug,
            String currentDocId,
            MessageEvaluateType messageEvaluateType,
            Set<Long> historyExpanded,
            Set<Long> historyCollapsed,
            Runnable refreshRows) {
    }
}
