package org.verbaria.server.headless.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.verbaria.server.headless.extension.gettext.GettextExtensions;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Implements <a href="/user-guide/translation-reuse/copy-trans">Copy Trans</a>:
 * fills empty translations in an iteration by pulling matching strings from
 * other documents anywhere on the server.
 *
 * <p>The three legacy rules ("On project mismatch", "On document mismatch",
 * "On context mismatch") each take one of {@link Action#Continue},
 * {@link Action#ContinueAsFuzzy}, or {@link Action#DontCopy}. Candidate
 * scoring follows the docs:</p>
 *
 * <ul>
 *   <li>A candidate is rejected outright if any fired condition has rule
 *       {@code DontCopy}.</li>
 *   <li>If any fired condition has {@code ContinueAsFuzzy}, the resulting
 *       target gets {@link ContentState#NeedReview} ("Fuzzy") instead of
 *       Translated/Approved.</li>
 *   <li>If multiple candidates remain, the most-recently-changed one wins.
 *       (The legacy heuristic preferred non-fuzzy → latest revision; we keep
 *       that priority by sorting "clean" candidates ahead of fuzzy ones.)</li>
 * </ul>
 */
@Service
public class CopyTransService {

    private static final Logger log = LoggerFactory.getLogger(CopyTransService.class);

    public enum Action { Continue, ContinueAsFuzzy, DontCopy }

    /** Per-run rule choices from the UI. */
    public record Options(Action onProjectMismatch,
                          Action onDocumentMismatch,
                          Action onContextMismatch) {
        public static Options permissive() {
            return new Options(Action.Continue, Action.Continue, Action.Continue);
        }
    }

    public record Progress(int processedFlows, int totalFlows,
                           int copied, int copiedAsFuzzy, int skipped) {}

    @FunctionalInterface
    public interface ProgressCallback {
        void report(Progress p);
    }

    private final ProjectIterationRepository iterationRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final LocaleRepository localeRepository;
    private final GettextExtensions gettext;

    public CopyTransService(ProjectIterationRepository iterationRepository,
                            TextFlowRepository textFlowRepository,
                            TextFlowTargetRepository targetRepository,
                            TextFlowTargetHistoryRepository historyRepository,
                            LocaleRepository localeRepository,
                            GettextExtensions gettext) {
        this.iterationRepository = iterationRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.localeRepository = localeRepository;
        this.gettext = gettext;
    }

    /**
     * Run Copy Trans for every locale active on {@code iterationId}. Returns
     * a single aggregated progress snapshot. {@code progress} may be null.
     */
    @Transactional
    public Progress runForIteration(Long iterationId, Options options, ProgressCallback progress) {
        HProjectIteration iter = iterationRepository.findById(iterationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Iteration not found: " + iterationId));
        List<HLocale> locales = resolveLocales(iter);
        if (locales.isEmpty()) {
            log.info("Copy Trans: iteration {} has no target locales — nothing to do", iterationId);
            return new Progress(0, 0, 0, 0, 0);
        }

        // Pre-compute totals across all locales for accurate progress reporting.
        int totalWork = 0;
        for (HLocale loc : locales) {
            totalWork += textFlowRepository
                    .findUntranslatedInIteration(iterationId, loc.getLocaleId())
                    .size();
        }
        int processed = 0, copied = 0, fuzzy = 0, skipped = 0;
        for (HLocale loc : locales) {
            List<HTextFlow> targets = textFlowRepository
                    .findUntranslatedInIteration(iterationId, loc.getLocaleId());
            for (HTextFlow src : targets) {
                MatchOutcome out = pickAndApply(src, loc, options);
                switch (out) {
                    case COPIED -> copied++;
                    case COPIED_AS_FUZZY -> fuzzy++;
                    case SKIPPED -> skipped++;
                }
                processed++;
                if (progress != null && (processed & 0x1F) == 0) {
                    progress.report(new Progress(processed, totalWork, copied, fuzzy, skipped));
                }
            }
        }
        Progress finalProgress = new Progress(processed, totalWork, copied, fuzzy, skipped);
        if (progress != null) progress.report(finalProgress);
        return finalProgress;
    }

    private enum MatchOutcome { COPIED, COPIED_AS_FUZZY, SKIPPED }

    /**
     * Pick the best candidate for {@code src} in {@code locale} subject to
     * {@code opts} and apply it. Creates or overwrites the target row;
     * snapshots prior content to history first.
     */
    private MatchOutcome pickAndApply(HTextFlow src, HLocale locale, Options opts) {
        if (src.getContentHash() == null) return MatchOutcome.SKIPPED;
        List<HTextFlowTarget> raw = textFlowRepository.findCopyTransCandidates(
                src.getContentHash(), src.getId(), locale.getLocaleId());
        if (raw.isEmpty()) return MatchOutcome.SKIPPED;

        // Evaluate each candidate against the rules; sort acceptable ones with
        // clean (non-fuzzy) ahead of fuzzy, then by lastChanged desc.
        HTextFlowTarget best = null;
        boolean bestIsFuzzy = false;
        for (HTextFlowTarget cand : raw) {
            HTextFlow cf = cand.getTextFlow();
            if (cf == null || cf.getDocument() == null
                    || cf.getDocument().getProjectIteration() == null
                    || cf.getDocument().getProjectIteration().getProject() == null) {
                continue;
            }
            // Same-project = same project id; doc same if same path + name.
            boolean projectMismatch = !Objects.equals(
                    cf.getDocument().getProjectIteration().getProject().getId(),
                    src.getDocument().getProjectIteration().getProject().getId());
            boolean documentMismatch = !Objects.equals(
                    cf.getDocument().getDocId(), src.getDocument().getDocId());
            String srcCtx = gettext.context(src);
            String candCtx = gettext.context(cf);
            boolean contextMismatch = !Objects.equals(srcCtx, candCtx);

            Action[] fired = {
                    projectMismatch ? opts.onProjectMismatch() : null,
                    documentMismatch ? opts.onDocumentMismatch() : null,
                    contextMismatch ? opts.onContextMismatch() : null};
            if (Arrays.stream(fired).anyMatch(a -> a == Action.DontCopy)) continue;
            boolean asFuzzy = Arrays.stream(fired)
                    .anyMatch(a -> a == Action.ContinueAsFuzzy);

            // Prefer the first non-fuzzy candidate (the query already returns
            // them lastChanged desc, so the first clean one is best).
            if (!asFuzzy) {
                if (best == null || bestIsFuzzy) {
                    best = cand;
                    bestIsFuzzy = false;
                    break;
                }
            } else if (best == null) {
                best = cand;
                bestIsFuzzy = true;
            }
        }
        if (best == null) return MatchOutcome.SKIPPED;

        // Apply: create/overwrite target row.
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(src.getId(), locale.getLocaleId())
                .orElseGet(() -> new HTextFlowTarget(src, locale));
        // Snapshot prior content to history if there was meaningful content.
        if (target.getId() != null && target.getState() != null
                && target.getState() != ContentState.New) {
            historyRepository.save(new HTextFlowTargetHistory(target));
        }
        target.setContents(best.getContents());
        ContentState newState = bestIsFuzzy ? ContentState.NeedReview
                : (best.getState() == ContentState.Approved
                        ? ContentState.Approved : ContentState.Translated);
        target.setState(newState);
        target.setTextFlowRevision(src.getRevision());
        targetRepository.save(target);
        return bestIsFuzzy ? MatchOutcome.COPIED_AS_FUZZY : MatchOutcome.COPIED;
    }

    /**
     * Resolve which locales to fill: iteration's customized list if any,
     * else the server's full enabled list. Source locale is excluded.
     */
    private List<HLocale> resolveLocales(HProjectIteration iter) {
        LocaleId sourceId = iter.getProject() != null
                && iter.getProject().getDefaultSourceLocale() != null
                ? iter.getProject().getDefaultSourceLocale().getLocaleId() : null;
        java.util.Collection<HLocale> base = iterationRepository
                .findProjectLocales(iter.getId())
                .orElseGet(() -> new java.util.ArrayList<>(localeRepository.findAll()));
        java.util.List<HLocale> out = new java.util.ArrayList<>();
        for (HLocale l : base) {
            if (sourceId != null && l.getLocaleId() != null
                    && sourceId.getId().equalsIgnoreCase(l.getLocaleId().getId())) continue;
            out.add(l);
        }
        return out;
    }
}
