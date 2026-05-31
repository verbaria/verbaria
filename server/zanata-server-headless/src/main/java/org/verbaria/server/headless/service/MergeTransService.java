package org.verbaria.server.headless.service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Implements <a href="/user-guide/translation-reuse/merge-trans">Merge
 * Translations</a>: copy matching Translated/Approved translations from one
 * source iteration into the current target iteration, one locale at a time.
 *
 * <p>Differs from {@link CopyTransService}:</p>
 * <ul>
 *   <li>Scope is exactly one source iteration (not the whole server).</li>
 *   <li>Matching is by source content within that iteration. Same-resId
 *       matches win when there are multiple identical sources.</li>
 *   <li>{@code keepExisting=true} skips text flows that already have a
 *       Translated/Approved target on the current side. Otherwise an
 *       existing target is overwritten only when the incoming translation
 *       is newer (later {@code lastChanged}).</li>
 * </ul>
 */
@Service
public class MergeTransService {

    private static final Logger log = LoggerFactory.getLogger(MergeTransService.class);

    public record Progress(int processedFlows, int totalFlows,
                           int copied, int skipped) {}

    @FunctionalInterface
    public interface ProgressCallback {
        void report(Progress p);
    }

    private final ProjectIterationRepository iterationRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final LocaleRepository localeRepository;

    public MergeTransService(ProjectIterationRepository iterationRepository,
                             TextFlowRepository textFlowRepository,
                             TextFlowTargetRepository targetRepository,
                             TextFlowTargetHistoryRepository historyRepository,
                             LocaleRepository localeRepository) {
        this.iterationRepository = iterationRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.localeRepository = localeRepository;
    }

    @Transactional
    public Progress runMerge(Long targetIterId, Long sourceIterId,
                             boolean keepExisting, ProgressCallback progress) {
        if (Objects.equals(targetIterId, sourceIterId)) {
            throw new IllegalArgumentException(
                    "Source and target iteration must differ");
        }
        HProjectIteration target = iterationRepository.findById(targetIterId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Target iteration not found: " + targetIterId));
        HProjectIteration source = iterationRepository.findById(sourceIterId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Source iteration not found: " + sourceIterId));

        List<HLocale> locales = resolveLocales(target);
        // Pre-compute totals so the progress bar is accurate across locales.
        int totalWork = 0;
        for (HLocale loc : locales) {
            totalWork += countMergeableFlows(target.getId(), loc.getLocaleId(), keepExisting);
        }

        int processed = 0, copied = 0, skipped = 0;
        for (HLocale loc : locales) {
            List<HTextFlow> targetFlows = keepExisting
                    ? textFlowRepository.findUntranslatedInIteration(target.getId(), loc.getLocaleId())
                    : findAllSourceFlows(target.getId());
            for (HTextFlow tf : targetFlows) {
                boolean did = tryMergeOne(tf, source, loc, keepExisting);
                if (did) copied++; else skipped++;
                processed++;
                if (progress != null && (processed & 0x1F) == 0) {
                    progress.report(new Progress(processed, totalWork, copied, skipped));
                }
            }
        }
        Progress finalP = new Progress(processed, totalWork, copied, skipped);
        if (progress != null) progress.report(finalP);
        log.info("Merge Trans target={} source={} copied={} skipped={}",
                targetIterId, sourceIterId, copied, skipped);
        return finalP;
    }

    private int countMergeableFlows(Long iterId, LocaleId locale, boolean keepExisting) {
        if (keepExisting) {
            return textFlowRepository.findUntranslatedInIteration(iterId, locale).size();
        }
        return findAllSourceFlows(iterId).size();
    }

    private List<HTextFlow> findAllSourceFlows(Long iterId) {
        // Use the existing per-doc count query as a starting point, but we
        // want a flat list of every text flow in the iteration. Resolve via
        // the iteration entity's documents directly.
        HProjectIteration it = iterationRepository.findById(iterId).orElse(null);
        if (it == null) return List.of();
        return it.getDocuments().values().stream()
                .flatMap(d -> textFlowRepository.findByDocument(d.getId()).stream())
                .collect(Collectors.toList());
    }

    private boolean tryMergeOne(HTextFlow targetFlow, HProjectIteration source,
                                HLocale locale, boolean keepExisting) {
        if (targetFlow.getContentHash() == null) return false;
        // Existing target on this side
        HTextFlowTarget existing = targetRepository
                .findByTextFlowAndLocale(targetFlow.getId(), locale.getLocaleId())
                .orElse(null);
        if (existing != null && keepExisting
                && (existing.getState() == ContentState.Translated
                || existing.getState() == ContentState.Approved)) {
            return false;
        }

        // Look for a matching source-side translation: same content hash,
        // scoped to the SOURCE iteration only, in Translated/Approved state.
        List<HTextFlowTarget> candidates = textFlowRepository
                .findCopyTransCandidates(targetFlow.getContentHash(),
                        targetFlow.getId(), locale.getLocaleId())
                .stream()
                .filter(c -> c.getTextFlow() != null
                        && c.getTextFlow().getDocument() != null
                        && c.getTextFlow().getDocument().getProjectIteration() != null
                        && Objects.equals(
                                c.getTextFlow().getDocument().getProjectIteration().getId(),
                                source.getId()))
                .collect(Collectors.toList());
        if (candidates.isEmpty()) return false;

        // Prefer the same resId, then the most-recently-changed.
        HTextFlowTarget best = candidates.stream()
                .filter(c -> Objects.equals(
                        c.getTextFlow().getResId(), targetFlow.getResId()))
                .findFirst()
                .orElse(candidates.get(0));

        // Don't overwrite with an older translation when not keeping existing.
        if (existing != null && !keepExisting && existing.getLastChanged() != null
                && best.getLastChanged() != null
                && !best.getLastChanged().after(existing.getLastChanged())) {
            return false;
        }

        HTextFlowTarget t = existing == null
                ? new HTextFlowTarget(targetFlow, locale) : existing;
        if (t.getId() != null && t.getState() != null
                && t.getState() != ContentState.New) {
            historyRepository.save(new HTextFlowTargetHistory(t));
        }
        t.setContents(best.getContents());
        t.setState(best.getState() == ContentState.Approved
                ? ContentState.Approved : ContentState.Translated);
        t.setTextFlowRevision(targetFlow.getRevision());
        targetRepository.save(t);
        return true;
    }

    private List<HLocale> resolveLocales(HProjectIteration iter) {
        LocaleId srcId = iter.getProject() != null
                && iter.getProject().getDefaultSourceLocale() != null
                ? iter.getProject().getDefaultSourceLocale().getLocaleId() : null;
        java.util.Collection<HLocale> base = iterationRepository
                .findProjectLocales(iter.getId())
                .orElseGet(() -> new java.util.ArrayList<>(localeRepository.findAll()));
        java.util.List<HLocale> out = new java.util.ArrayList<>();
        for (HLocale l : base) {
            if (srcId != null && l.getLocaleId() != null
                    && srcId.getId().equalsIgnoreCase(l.getLocaleId().getId())) continue;
            out.add(l);
        }
        return out;
    }
}
