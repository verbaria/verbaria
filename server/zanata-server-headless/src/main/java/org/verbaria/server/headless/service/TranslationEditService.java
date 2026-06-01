package org.verbaria.server.headless.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.repository.TextFlowTargetReviewCommentRepository;

@Service
public class TranslationEditService {

    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;

    public TranslationEditService(TextFlowRepository textFlowRepository,
                                  TextFlowTargetRepository targetRepository,
                                  TextFlowTargetReviewCommentRepository reviewCommentRepository,
                                  LocaleRepository localeRepository,
                                  AccountRepository accountRepository) {
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
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
        syncSourceTarget(textFlow, fresh, editor);
    }

    private void syncSourceTarget(HTextFlow textFlow, String content, HPerson editor) {
        var doc = textFlow.getDocument();
        if (doc == null) return;
        HLocale srcLocale = doc.getLocale();
        if (srcLocale == null || srcLocale.getLocaleId() == null) return;
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
        textFlow.setConsuloFileExt(fresh);
        textFlowRepository.save(textFlow);
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
        if (comment != null && !comment.isBlank() && reviewerUsername != null) {
            HPerson reviewer = accountRepository.findByUsername(reviewerUsername)
                    .map(HAccount::getPerson)
                    .orElse(null);
            if (reviewer != null) {
                HTextFlowTargetReviewComment rc = new HTextFlowTargetReviewComment(
                        target, comment.trim(), reviewer, null);
                reviewCommentRepository.save(rc);
            }
        }
        target.setState(newState);
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
        return target.getState();
    }

    @Transactional
    public ContentState save(Long textFlowId, LocaleId localeId, String newContent) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HLocale locale = localeRepository.findByLocaleId(localeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not found: " + localeId.getId()));
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseGet(() -> new HTextFlowTarget(textFlow, locale));

        // History is maintained by HTextFlowTarget's @PreUpdate listener; a
        // manual write here duplicated the (target_id, version_num) row.
        target.setContents(List.of(newContent == null ? "" : newContent));
        target.setState(ContentState.Translated);
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
        return target.getState();
    }
}
