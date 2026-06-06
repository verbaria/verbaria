package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.repository.TranslateFilterMode;
import org.zanata.common.ActivityType;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.zanata.model.IsEntityWithType;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.verbaria.server.headless.extension.comment.CommentExtensions;
import org.verbaria.server.headless.extension.gettext.GettextExtensions;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.verbaria.server.headless.event.ContentChangedEvent;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.repository.TextFlowTargetReviewCommentRepository;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@Service
public class TranslationEditService {

    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final TextFlowExtensionStore extensionStore;
    private final GettextExtensions gettext;
    private final CommentExtensions comments;
    private final ApplicationEventPublisher eventPublisher;

    public TranslationEditService(TextFlowRepository textFlowRepository,
                                  TextFlowTargetRepository targetRepository,
                                  TextFlowTargetReviewCommentRepository reviewCommentRepository,
                                  LocaleRepository localeRepository,
                                  AccountRepository accountRepository,
                                  TextFlowExtensionStore extensionStore,
                                  GettextExtensions gettext,
                                  CommentExtensions comments,
                                  ApplicationEventPublisher eventPublisher) {
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
        this.extensionStore = extensionStore;
        this.gettext = gettext;
        this.comments = comments;
        this.eventPublisher = eventPublisher;
    }

    private void publishStatsChanged(HTextFlow textFlow) {
        publishChanged(textFlow, null, null, null, 0);
    }

    private void publishChanged(HTextFlow textFlow, String actorUsername,
            IsEntityWithType target, ActivityType activityType, int wordCount) {
        if (textFlow == null || textFlow.getDocument() == null) {
            return;
        }
        HProjectIteration iteration =
                textFlow.getDocument().getProjectIteration();
        if (iteration == null || iteration.getProject() == null) {
            return;
        }
        eventPublisher.publishEvent(new ContentChangedEvent(
                iteration.getProject().getSlug(), actorUsername, iteration,
                target, activityType, wordCount));
    }

    public String gettextContext(HTextFlow flow) {
        return gettext.context(flow);
    }

    public String gettextReferences(HTextFlow flow) {
        return gettext.references(flow);
    }

    public String gettextFlags(HTextFlow flow) {
        return gettext.flags(flow);
    }

    public String sourceComment(HTextFlow flow) {
        return comments.sourceComment(flow);
    }

    /**
     * One page of text flows for the translate view, with their lazy
     * {@code extensions} collection initialised in the same session so the grid
     * can render the (then detached) rows without a LazyInitializationException.
     */
    @Transactional(readOnly = true)
    public List<HTextFlow> pageWithExtensions(Long docId, LocaleId locale,
            String query, int stateMode, Pageable page) {
        List<HTextFlow> flows = textFlowRepository.pageForTranslateView(
                docId, locale, query, stateMode, page);
        if (!flows.isEmpty()) {
            textFlowRepository.findWithExtensions(
                    flows.stream().map(HTextFlow::getId).toList());
        }
        return flows;
    }

    /** Source text, source locale and gettext context for one text flow. */
    public record AiSource(String source, LocaleId sourceLocale, String context) {}

    /** {@link AiSource} plus the text flow id, for bulk AI translation. */
    public record AiSourceRow(Long textFlowId, String source,
            LocaleId sourceLocale, String context) {}

    @Transactional(readOnly = true)
    public AiSource aiSource(Long textFlowId) {
        HTextFlow tf = textFlowRepository.findWithExtensions(List.of(textFlowId))
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        return new AiSource(firstContent(tf),
                tf.getDocument().getLocale().getLocaleId(), gettextContext(tf));
    }

    @Transactional(readOnly = true)
    public boolean hasTranslation(Long textFlowId, LocaleId locale) {
        return targetRepository.findByTextFlowAndLocale(textFlowId, locale)
                .map(t -> hasContent(t.getContents()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<AiSourceRow> aiUntranslatedSources(Long docId, LocaleId locale,
            int limit) {
        List<HTextFlow> flows = pageWithExtensions(docId, locale, "",
                TranslateFilterMode.INCOMPLETE.code(), PageRequest.of(0, limit));
        if (flows.isEmpty()) {
            return List.of();
        }
        Set<Long> alreadyTranslated = new HashSet<>();
        for (HTextFlowTarget t : targetRepository.findByTextFlowIdsAndLocale(
                flows.stream().map(HTextFlow::getId).toList(), locale)) {
            if (hasContent(t.getContents())) {
                alreadyTranslated.add(t.getTextFlow().getId());
            }
        }
        List<AiSourceRow> out = new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) {
            // Skip anything that already carries a translation (fuzzy/rejected):
            // bulk AI only fills truly empty slots.
            if (alreadyTranslated.contains(tf.getId())) {
                continue;
            }
            out.add(new AiSourceRow(tf.getId(), firstContent(tf),
                    tf.getDocument().getLocale().getLocaleId(),
                    gettextContext(tf)));
        }
        return out;
    }

    private static boolean hasContent(List<String> contents) {
        return contents != null && !contents.isEmpty()
                && contents.get(0) != null && !contents.get(0).isBlank();
    }

    private static String firstContent(HTextFlow tf) {
        return tf.getContents() == null || tf.getContents().isEmpty()
                || tf.getContents().get(0) == null
                ? "" : tf.getContents().get(0);
    }

    @Transactional
    public void updateSourceComment(Long textFlowId, String comment) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        String fresh = comment == null ? "" : comment.trim();
        if (fresh.isEmpty()) {
            extensionStore.remove(textFlow, SimpleComment.ID);
        } else {
            extensionStore.put(textFlow, new SimpleComment(fresh));
        }
        textFlowRepository.save(textFlow);
    }

    @Transactional
    public void updateSource(Long textFlowId, String newSource) {
        updateSource(textFlowId, newSource, null);
    }

    @Transactional
    public void updateSource(Long textFlowId, String newSource, String editorUsername) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        String oldSource = textFlow.getContents() == null || textFlow.getContents().isEmpty()
                ? "" : textFlow.getContents().get(0);
        String fresh = newSource == null ? "" : newSource;
        if (oldSource.equals(fresh)) return;
        textFlow.setContents(List.of(fresh));
        textFlow.setRevision(textFlow.getRevision() + 1);
        textFlowRepository.save(textFlow);
        HPerson editor = editorUsername == null ? null
                : accountRepository.findByUsername(editorUsername)
                        .map(HAccount::getPerson).orElse(null);
        HTextFlowTarget srcTarget = syncSourceTarget(textFlow, fresh, editor);
        publishChanged(textFlow, editorUsername, srcTarget,
                ActivityType.UPDATE_TRANSLATION, wordCount(textFlow));
    }

    private HTextFlowTarget syncSourceTarget(HTextFlow textFlow, String content,
            HPerson editor) {
        var doc = textFlow.getDocument();
        if (doc == null) return null;
        HLocale srcLocale = doc.getLocale();
        if (srcLocale == null || srcLocale.getLocaleId() == null) return null;
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlow.getId(), srcLocale.getLocaleId())
                .orElseGet(() -> new HTextFlowTarget(textFlow, srcLocale));
        target.setContents(List.of(content));
        target.setState(ContentState.Translated);
        target.setTextFlowRevision(textFlow.getRevision());
        if (editor != null) {
            target.setTranslator(editor);
            target.setLastModifiedBy(editor);
        }
        targetRepository.save(target);
        return target;
    }

    /**
     * Change the consulo sub-file extension of a source text flow. Reviewers
     * may set any extension (it drives editor highlighting and the file pull
     * recreates). Empty string keeps it a raw file with no extension.
     */
    @Transactional
    public void updateConsuloFileExt(Long textFlowId, String newExt) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        String fresh = newExt == null ? "" : newExt.trim();
        if (fresh.startsWith(".")) {
            fresh = fresh.substring(1);
        }
        extensionStore.put(textFlow, new ConsuloSubFile(fresh));
        textFlowRepository.save(textFlow);
    }

    public String consuloContentType(HTextFlow flow) {
        return extensionStore.contentType(flow).orElse(null);
    }

    public boolean isConsuloFile(HTextFlow flow) {
        return extensionStore.get(flow, ConsuloSubFile.class)
                .map(c -> c.getExtension() != null).orElse(false);
    }

    public String consuloExtension(HTextFlow flow) {
        return extensionStore.get(flow, ConsuloSubFile.class)
                .map(ConsuloSubFile::getExtension).orElse(null);
    }

    @Transactional
    public ContentState changeState(Long textFlowId, LocaleId localeId, ContentState newState) {
        return changeState(textFlowId, localeId, newState, null, null);
    }

    /**
     * Change a target's state and (optionally) attach a review comment from
     * {@code reviewerUsername}. Used by the reject-with-reason dialog: docs
     * mandate that rejections include a reason so the translator knows what
     * to fix. {@code comment}/{@code reviewerUsername} may be {@code null}
     * to skip the comment (e.g. when approving).
     */
    @Transactional
    public ContentState changeState(Long textFlowId, LocaleId localeId,
                                    ContentState newState, String comment,
                                    String reviewerUsername) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HLocale locale = localeRepository.findByLocaleId(localeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not found: " + localeId.getId()));
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No translation to change state — type and save first"));
        boolean hasContent = target.getContents() != null
                && !target.getContents().isEmpty()
                && target.getContents().get(0) != null
                && !target.getContents().get(0).isBlank();
        if (!hasContent) {
            throw new IllegalStateException(
                    "Cannot change state of an empty translation");
        }
        // No-op if the state isn't actually changing: avoids a pointless history
        // row (and keeps approve/reject idempotent when the row is already in
        // that state).
        if (target.getState() == newState) {
            return target.getState();
        }
        // History is written by HTextFlowTarget's @PreUpdate listener on the
        // save below; writing one here too duplicated the (target_id,
        // version_num) row and crashed the next state change.
        // Attach the review comment BEFORE the state change so the comment's
        // targetVersion matches the version that's being rejected.
        HPerson reviewer = reviewerUsername == null ? null
                : accountRepository.findByUsername(reviewerUsername)
                        .map(HAccount::getPerson).orElse(null);
        if (comment != null && !comment.isBlank() && reviewer != null) {
            HTextFlowTargetReviewComment rc = new HTextFlowTargetReviewComment(
                    target, comment.trim(), reviewer, null);
            reviewCommentRepository.save(rc);
        }
        target.setState(newState);
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
        boolean review = reviewerUsername != null
                && (newState == ContentState.Approved
                        || newState == ContentState.Rejected);
        publishChanged(textFlow, review ? reviewerUsername : null, target,
                review ? ActivityType.REVIEWED_TRANSLATION : null,
                wordCount(textFlow));
        return target.getState();
    }

    @Transactional
    public ContentState save(Long textFlowId, LocaleId localeId, String newContent) {
        return save(textFlowId, localeId, newContent, null);
    }

    @Transactional
    public ContentState save(Long textFlowId, LocaleId localeId,
            String newContent, String editorUsername) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HLocale locale = localeRepository.findByLocaleId(localeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not found: " + localeId.getId()));
        // Resolve the editor up front: doing it after the target is modified
        // would auto-flush the dirty target mid-method (queries trigger a flush),
        // firing the history listener prematurely.
        HPerson editor = editorUsername == null ? null
                : accountRepository.findByUsername(editorUsername)
                        .map(HAccount::getPerson).orElse(null);
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseGet(() -> new HTextFlowTarget(textFlow, locale));

        // History is maintained by HTextFlowTarget's @PreUpdate listener; a
        // manual write here duplicated the (target_id, version_num) row.
        target.setContents(List.of(newContent == null ? "" : newContent));
        target.setState(ContentState.Translated);
        target.setTextFlowRevision(textFlow.getRevision());
        // Attribute the edit to the editor so the translation is credited to the
        // real translator, not to whoever first created the row (e.g. a sync bot
        // that pushed it). Without this the stale author would surface as the
        // Co-authored-by in pull-generated commit messages.
        if (editor != null) {
            target.setTranslator(editor);
            target.setLastModifiedBy(editor);
        }
        targetRepository.save(target);
        publishChanged(textFlow, editorUsername, target,
                ActivityType.UPDATE_TRANSLATION, wordCount(textFlow));
        return target.getState();
    }

    private static int wordCount(HTextFlow textFlow) {
        return textFlow.getWordCount() == null ? 0
                : textFlow.getWordCount().intValue();
    }

    /**
     * One-click "still valid": acknowledge that an existing translation is fine
     * against the changed source by re-stamping it to the current source
     * revision. Content and state are untouched, so this only clears the
     * "needs review" warning.
     */
    @Transactional
    public void markReviewed(Long textFlowId, LocaleId localeId) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No translation to review"));
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
    }
}
