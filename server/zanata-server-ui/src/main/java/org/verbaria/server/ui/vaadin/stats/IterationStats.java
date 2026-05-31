package org.verbaria.server.ui.vaadin.stats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.zanata.common.ContentState;
import org.zanata.model.HLocale;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Aggregated translation statistics for a single project iteration, computed
 * from the source text-flow word count, the set of enabled locales, and the
 * locale x state word-count breakdown.
 */
public final class IterationStats {

    /** Translator words-per-hour rate used historically by Zanata. */
    public static final double WORDS_PER_HOUR = 250.0;

    public final long totalSourceWords;
    public final int localeCount;
    public final long totalPossibleWords;
    public final long translatedWords;
    public final long approvedWords;
    public final long needsReviewWords;
    public final double translatedPct;
    public final double approvedPct;
    public final double hoursRemaining;
    public final List<HLocale> enabledLocales;
    public final List<LocaleStats> perLocale;

    private IterationStats(long totalSourceWords,
                           int localeCount,
                           long totalPossibleWords,
                           long translatedWords,
                           long approvedWords,
                           long needsReviewWords,
                           double translatedPct,
                           double approvedPct,
                           double hoursRemaining,
                           List<HLocale> enabledLocales,
                           List<LocaleStats> perLocale) {
        this.totalSourceWords = totalSourceWords;
        this.localeCount = localeCount;
        this.totalPossibleWords = totalPossibleWords;
        this.translatedWords = translatedWords;
        this.approvedWords = approvedWords;
        this.needsReviewWords = needsReviewWords;
        this.translatedPct = translatedPct;
        this.approvedPct = approvedPct;
        this.hoursRemaining = hoursRemaining;
        this.enabledLocales = enabledLocales;
        this.perLocale = perLocale;
    }

    public static IterationStats compute(Long iterationId,
                                         ProjectIterationRepository iterationRepository,
                                         TextFlowTargetRepository targetRepository,
                                         LocaleRepository localeRepository) {
        long totalSourceWords = iterationRepository.sumSourceWordCount(iterationId);

        // Prefer the project's customized locale list when overrideLocales=true;
        // else fall back to every active server locale.
        List<HLocale> enabledLocales = new ArrayList<>();
        var customized = iterationRepository.findProjectLocales(iterationId);
        if (customized.isPresent()) {
            for (HLocale l : customized.get()) {
                if (l.isActive()) enabledLocales.add(l);
            }
        }
        if (enabledLocales.isEmpty()) {
            for (HLocale l : localeRepository.findAll()) {
                if (l.isActive()) enabledLocales.add(l);
            }
        }
        // Always include the project's source locale (so users can edit
        // source strings) even if it's not in customizedLocales.
        HLocale source = iterationRepository.findProjectSourceLocale(iterationId).orElse(null);
        if (source != null && source.isActive()
                && enabledLocales.stream().noneMatch(l -> l.getId().equals(source.getId()))) {
            enabledLocales.add(source);
        }
        enabledLocales.sort(Comparator.comparing(
                l -> (l.getDisplayName() == null ? "" : l.getDisplayName()),
                String.CASE_INSENSITIVE_ORDER));
        int localeCount = enabledLocales.size();
        long totalPossibleWords = totalSourceWords * Math.max(localeCount, 1);

        // locale id -> state -> words
        Map<Long, EnumMap<ContentState, Long>> byLocale = new HashMap<>();
        for (Object[] row : targetRepository.aggregateWordsByLocaleAndState(iterationId)) {
            HLocale locale = (HLocale) row[0];
            ContentState state = (ContentState) row[1];
            Number words = (Number) row[2];
            if (locale == null || state == null || words == null) continue;
            byLocale.computeIfAbsent(locale.getId(), k -> new EnumMap<>(ContentState.class))
                    .merge(state, words.longValue(), Long::sum);
        }

        long translatedWords = 0;
        long approvedWords = 0;
        long needsReviewWords = 0;
        List<LocaleStats> perLocale = new ArrayList<>();
        for (HLocale l : enabledLocales) {
            EnumMap<ContentState, Long> counts = byLocale.getOrDefault(
                    l.getId(), new EnumMap<>(ContentState.class));
            long localApproved = counts.getOrDefault(ContentState.Approved, 0L);
            long localTranslatedOnly = counts.getOrDefault(ContentState.Translated, 0L);
            long localTranslated = localApproved + localTranslatedOnly;
            long localNeedsReview = counts.getOrDefault(ContentState.NeedReview, 0L);
            translatedWords += localTranslated;
            approvedWords += localApproved;
            needsReviewWords += localNeedsReview;
            double pct = totalSourceWords == 0 ? 0.0
                    : (localTranslated * 100.0) / totalSourceWords;
            perLocale.add(new LocaleStats(l, totalSourceWords, localTranslated,
                    localApproved, localNeedsReview, pct));
        }

        double translatedPct = totalPossibleWords == 0 ? 0.0
                : (translatedWords * 100.0) / totalPossibleWords;
        double approvedPct = totalPossibleWords == 0 ? 0.0
                : (approvedWords * 100.0) / totalPossibleWords;
        double hoursRemaining = Math.max(0L, totalPossibleWords - translatedWords) / WORDS_PER_HOUR;

        return new IterationStats(totalSourceWords, localeCount, totalPossibleWords,
                translatedWords, approvedWords, needsReviewWords,
                translatedPct, approvedPct, hoursRemaining,
                Collections.unmodifiableList(enabledLocales),
                Collections.unmodifiableList(perLocale));
    }

    public static final class LocaleStats {
        public final HLocale locale;
        public final long totalSourceWords;
        public final long translatedWords;
        public final long approvedWords;
        public final long needsReviewWords;
        public final double translatedPct;

        public LocaleStats(HLocale locale, long totalSourceWords, long translatedWords,
                           long approvedWords, long needsReviewWords, double translatedPct) {
            this.locale = locale;
            this.totalSourceWords = totalSourceWords;
            this.translatedWords = translatedWords;
            this.approvedWords = approvedWords;
            this.needsReviewWords = needsReviewWords;
            this.translatedPct = translatedPct;
        }
    }
}
