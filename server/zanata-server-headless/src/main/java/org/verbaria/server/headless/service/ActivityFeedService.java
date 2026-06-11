package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.verbaria.server.headless.extension.gettext.GettextExtensions;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Builds a chronological feed of translation actions (saved, approved,
 * rejected, marked needs-review) by merging the latest state of each target
 * with the historical states it has passed through. Each record is "person P
 * set state S on doc D at time T", filterable by user and project.
 */
@Service
public class ActivityFeedService {

    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final GettextExtensions gettext;

    public ActivityFeedService(TextFlowTargetRepository targetRepository,
            TextFlowTargetHistoryRepository historyRepository,
            AccountRepository accountRepository,
            ProjectRepository projectRepository,
            LocaleRepository localeRepository,
            GettextExtensions gettext) {
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.gettext = gettext;
    }

    public record Entry(String actorUsername, String actorName,
            String actorEmail, ContentState state, String projectSlug,
            String projectName, String versionSlug, String docId,
            String localeId, String key, String resId, String source,
            String value, String previousValue, Date when) {}

    private record Raw(Long textFlowId, String localeId, Integer version,
            String value, ContentState state, String actorUsername,
            String actorName, String actorEmail, String projectSlug,
            String projectName, String versionSlug, String docId, String key,
            String resId, String source, Date when) {}

    public record Actor(String username, String displayName) {}

    public record ProjectOption(String slug, String name) {}

    public record LocaleOption(String id, String displayName) {}

    @Transactional(readOnly = true)
    public List<Actor> actors() {
        List<Actor> out = new ArrayList<>();
        for (var a : accountRepository.findAll()) {
            HPerson person = a.getPerson();
            if (person == null) {
                continue;
            }
            String name = person.getName() == null || person.getName().isBlank()
                    ? a.getUsername() : person.getName();
            out.add(new Actor(a.getUsername(), name));
        }
        out.sort(Comparator.comparing(Actor::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public List<ProjectOption> projects() {
        List<ProjectOption> out = new ArrayList<>();
        for (HProject p : projectRepository.findAll()) {
            out.add(new ProjectOption(p.getSlug(),
                    p.getName() == null ? p.getSlug() : p.getName()));
        }
        out.sort(Comparator.comparing(ProjectOption::name,
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    @Transactional(readOnly = true)
    public List<LocaleOption> locales() {
        List<LocaleOption> out = new ArrayList<>();
        for (HLocale l : localeRepository.findAll()) {
            if (l.getLocaleId() == null) {
                continue;
            }
            String id = l.getLocaleId().getId();
            String name = l.getDisplayName() == null || l.getDisplayName().isBlank()
                    ? id : l.getDisplayName() + " (" + id + ")";
            out.add(new LocaleOption(id, name));
        }
        out.sort(Comparator.comparing(LocaleOption::displayName,
                String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    // Open-ended sentinel bounds so the date filter is always two concrete
    // timestamps — avoids binding an untyped NULL for a timestamp parameter,
    // which PostgreSQL rejects ("could not determine data type of parameter").
    private static final Date MIN_DATE = new Date(0L);
    private static final Date MAX_DATE =
            Date.from(java.time.Instant.parse("9999-12-31T23:59:59Z"));

    /**
     * One page of the merged activity feed (current targets + history), newest
     * first. Both sources are sorted by time, so the global page {@code [offset,
     * offset+limit)} is contained in the top {@code offset+limit} of each — we
     * over-fetch that many from each, merge, sort, then slice.
     */
    @Transactional(readOnly = true)
    public List<Entry> recent(String username, String projectSlug,
            String localeId, Date from, Date to, int offset, int limit) {
        String u = blankToNull(username);
        String p = blankToNull(projectSlug);
        LocaleId loc = blankToNull(localeId) == null ? null
                : new LocaleId(localeId);
        Date fromD = from == null ? MIN_DATE : from;
        Date toD = to == null ? MAX_DATE : to;
        int over = offset + limit;
        Pageable page = PageRequest.of(0, Math.max(1, over));

        List<Raw> raws = new ArrayList<>();
        for (HTextFlowTarget t : targetRepository.findRecentActivity(u, p, loc, fromD, toD, page)) {
            Raw r = rawFromTarget(t);
            if (r != null && r.when() != null) {
                raws.add(r);
            }
        }
        for (HTextFlowTargetHistory h : historyRepository.findRecentActivity(u, p, loc, fromD, toD, page)) {
            Raw r = rawFromHistory(h);
            if (r != null && r.when() != null) {
                raws.add(r);
            }
        }
        raws.sort(Comparator.comparing(Raw::when).reversed());
        if (offset >= raws.size()) {
            return List.of();
        }
        List<Raw> pageRaws = new ArrayList<>(
                raws.subList(offset, Math.min(raws.size(), over)));

        // Reconstruct the value each change replaced. The prior value is looked
        // up from the FULL version chain (every target + history row for the
        // involved text flows, unfiltered) so "before" shows regardless of who
        // set it, whether they match the active filters, or whether that
        // version is inside the activity window.
        Map<String, NavigableMap<Integer, String>> chains =
                buildValueChains(pageRaws);
        List<Entry> out = new ArrayList<>(pageRaws.size());
        for (Raw r : pageRaws) {
            String prev = previousValue(chains, r);
            out.add(new Entry(r.actorUsername(), r.actorName(), r.actorEmail(),
                    r.state(), r.projectSlug(), r.projectName(), r.versionSlug(),
                    r.docId(), r.localeId(), r.key(), r.resId(), r.source(),
                    r.value(), prev, r.when()));
        }
        return out;
    }

    private Map<String, NavigableMap<Integer, String>> buildValueChains(
            List<Raw> raws) {
        Set<Long> textFlowIds = new HashSet<>();
        for (Raw r : raws) {
            if (r.textFlowId() != null) {
                textFlowIds.add(r.textFlowId());
            }
        }
        Map<String, NavigableMap<Integer, String>> chains = new HashMap<>();
        if (textFlowIds.isEmpty()) {
            return chains;
        }
        for (HTextFlowTarget t : targetRepository.findForTextFlows(textFlowIds)) {
            putVersion(chains, t.getTextFlow().getId(), localeId(t),
                    t.getVersionNum(), firstContent(t.getContents()));
        }
        for (HTextFlowTargetHistory h : historyRepository.findForTextFlows(textFlowIds)) {
            HTextFlowTarget t = h.getTextFlowTarget();
            if (t == null) {
                continue;
            }
            putVersion(chains, t.getTextFlow().getId(), localeId(t),
                    h.getVersionNum(), firstContent(h.getContents()));
        }
        return chains;
    }

    private static void putVersion(
            Map<String, NavigableMap<Integer, String>> chains, Long tfId,
            String localeId, Integer version, String value) {
        if (tfId == null || version == null) {
            return;
        }
        chains.computeIfAbsent(tfId + "|" + localeId, k -> new TreeMap<>())
                .put(version, value);
    }

    private static String previousValue(
            Map<String, NavigableMap<Integer, String>> chains, Raw r) {
        if (r.version() == null) {
            return null;
        }
        NavigableMap<Integer, String> chain =
                chains.get(r.textFlowId() + "|" + r.localeId());
        if (chain == null) {
            return null;
        }
        Map.Entry<Integer, String> lower = chain.lowerEntry(r.version());
        return lower == null ? null : lower.getValue();
    }

    private Raw rawFromTarget(HTextFlowTarget t) {
        if (t.getState() == null || t.getState() == ContentState.New) {
            return null;
        }
        return rawOf(t.getLastModifiedBy(), t.getState(), t.getTextFlow(),
                localeId(t), t.getVersionNum(), firstContent(t.getContents()),
                t.getLastChanged());
    }

    private Raw rawFromHistory(HTextFlowTargetHistory h) {
        if (h.getState() == null || h.getState() == ContentState.New) {
            return null;
        }
        HTextFlowTarget t = h.getTextFlowTarget();
        if (t == null) {
            return null;
        }
        return rawOf(h.getLastModifiedBy(), h.getState(), t.getTextFlow(),
                localeId(t), h.getVersionNum(), firstContent(h.getContents()),
                h.getLastChanged());
    }

    private Raw rawOf(HPerson actor, ContentState state, HTextFlow tf,
            String localeId, Integer version, String value, Date when) {
        if (actor == null || tf == null) {
            return null;
        }
        HDocument doc = tf.getDocument();
        if (doc == null) {
            return null;
        }
        HProjectIteration it = doc.getProjectIteration();
        HProject p = it == null ? null : it.getProject();
        if (p == null) {
            return null;
        }
        String username = actor.getAccount() == null ? null
                : actor.getAccount().getUsername();
        String context = gettext.context(tf);
        String key = context == null || context.isBlank()
                ? tf.getResId() : context;
        return new Raw(tf.getId(), localeId, version, value, state, username,
                actor.getName(), actor.getEmail(), p.getSlug(), p.getName(),
                it.getSlug(), doc.getDocId(), key, tf.getResId(),
                firstContent(tf.getContents()), when);
    }

    private static String firstContent(List<String> contents) {
        return contents == null || contents.isEmpty() ? "" : contents.get(0);
    }

    private static String localeId(HTextFlowTarget t) {
        return t.getLocale() != null && t.getLocale().getLocaleId() != null
                ? t.getLocale().getLocaleId().getId() : "";
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
