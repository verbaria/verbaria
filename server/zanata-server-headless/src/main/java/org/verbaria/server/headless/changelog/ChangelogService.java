package org.verbaria.server.headless.changelog;

import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.changelog.LockChangelog.Format;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.zanata.common.LocaleId;
import org.zanata.model.HPerson;

/**
 * Renders a changelog between two locks, deriving the {@code Co-authored-by}
 * identities from the server's own translation history (the lock no longer
 * carries translators). The previous lock's {@code generatedAt} is the baseline
 * for "who translated since".
 */
@Service
public class ChangelogService {

    private final TextFlowTargetRepository targetRepository;

    public ChangelogService(TextFlowTargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    @Transactional(readOnly = true)
    public String render(VerbariaLock before, VerbariaLock after, Format format,
            Collection<String> excludeAuthors) {
        LockChangelog changelog = LockChangelog.between(before, after)
                .excludeAuthors(excludeAuthors);
        changelog.withCoAuthors(
                collectAuthors(before, after, changelog.changedTranslations()));
        return changelog.render(format);
    }

    private Set<String> collectAuthors(VerbariaLock before, VerbariaLock after,
            Map<String, TreeSet<String>> changed) {
        Set<String> authors = new TreeSet<>();
        if (after == null || after.getProjectVersion() == null
                || changed.isEmpty()) {
            return authors;
        }
        Date since = since(before);
        String project = after.getProject();
        boolean glob = project != null
                && (project.contains("*") || project.contains("?"));
        String version = after.getProjectVersion();
        for (Map.Entry<String, TreeSet<String>> e : changed.entrySet()) {
            String docKey = e.getKey();
            String slug;
            String docId;
            if (glob) {
                int slash = docKey.indexOf('/');
                if (slash <= 0) {
                    continue;
                }
                slug = docKey.substring(0, slash);
                docId = docKey.substring(slash + 1);
            } else {
                slug = project;
                docId = docKey;
            }
            if (slug == null) {
                continue;
            }
            for (String locale : e.getValue()) {
                for (HPerson p : targetRepository.findTranslatorsSince(slug,
                        version, docId, new LocaleId(locale), since)) {
                    String who = formatPerson(p);
                    if (who != null) {
                        authors.add(who);
                    }
                }
            }
        }
        return authors;
    }

    private static Date since(VerbariaLock before) {
        if (before == null || before.getGeneratedAt() == null) {
            return new Date(0L);
        }
        try {
            return Date.from(Instant.parse(before.getGeneratedAt()));
        } catch (RuntimeException e) {
            return new Date(0L);
        }
    }

    private static String formatPerson(HPerson person) {
        if (person == null) {
            return null;
        }
        String name = person.getName();
        String email = person.getEmail();
        boolean hasName = name != null && !name.isBlank();
        boolean hasEmail = email != null && !email.isBlank();
        if (hasName && hasEmail) {
            return name.trim() + " <" + email.trim() + ">";
        }
        if (hasName) {
            return name.trim();
        }
        if (hasEmail) {
            return email.trim() + " <" + email.trim() + ">";
        }
        return null;
    }
}
