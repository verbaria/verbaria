package org.verbaria.server.headless.stats;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.verbaria.server.headless.event.ProjectStatsChangedEvent;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Caches the (word-based) translated percentage per project so the Projects
 * overview doesn't recompute {@link IterationStats} for every project on every
 * load. Entries are dropped when a {@link ProjectStatsChangedEvent} fires for
 * the project (translation/source change), so the figure stays fresh without a
 * time-based TTL.
 */
@Component
public class ProjectStatsCache {

    private final ProjectIterationRepository iterationRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;
    private final Map<String, Stats> cache = new ConcurrentHashMap<>();

    public ProjectStatsCache(ProjectIterationRepository iterationRepository,
            TextFlowTargetRepository targetRepository,
            LocaleRepository localeRepository) {
        this.iterationRepository = iterationRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
    }

    /** Word totals for a project, so callers can aggregate (e.g. by group). */
    public record Stats(long translatedWords, long possibleWords) {
        public double percent() {
            return possibleWords == 0 ? 0.0
                    : translatedWords * 100.0 / possibleWords;
        }
    }

    public Stats statsFor(String projectSlug) {
        Stats cached = cache.get(projectSlug);
        if (cached != null) {
            return cached;
        }
        Stats computed = compute(projectSlug);
        cache.put(projectSlug, computed);
        return computed;
    }

    public double translatedPercent(String projectSlug) {
        return statsFor(projectSlug).percent();
    }

    // AFTER_COMMIT so a read racing the change can't re-cache the pre-commit
    // value; fallbackExecution keeps it working if ever published outside a tx.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void onChanged(ProjectStatsChangedEvent event) {
        cache.remove(event.projectSlug());
    }

    private Stats compute(String projectSlug) {
        long translated = 0;
        long possible = 0;
        for (Long iterId : iterationRepository
                .findIterationIdsByProjectSlug(projectSlug)) {
            IterationStats s = IterationStats.compute(iterId,
                    iterationRepository, targetRepository, localeRepository);
            translated += s.translatedWords;
            possible += s.totalPossibleWords;
        }
        return new Stats(translated, possible);
    }
}
