/*
 * Copyright 2026, Verbaria contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.lock;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import org.zanata.client.lock.VerbariaLock.DocumentLock;
import org.zanata.client.lock.VerbariaLock.SourceLock;
import org.zanata.client.lock.VerbariaLock.TranslationLock;

/**
 * Diffs two {@link VerbariaLock}s and renders the change set describing
 * <em>what</em> changed and <em>by whom</em>, in one of two
 * {@link Format formats}:
 * <ul>
 *   <li>{@code GIT_COMMIT} (default) &mdash; a git commit message whose
 *       translators become {@code Co-authored-by} trailers (GitHub-recognised
 *       attribution);</li>
 *   <li>{@code MARKDOWN} &mdash; a changelog for a pull-request body.</li>
 * </ul>
 * An empty diff renders as the empty string, which callers (e.g. the
 * scheduled-sync workflow) use as the "nothing changed" signal.
 *
 * <pre>
 * chore(i18n): sync 2 docs (de, fr, ja)
 *
 * Updated:
 *   user-guide/intro   de, fr
 * Added:
 *   client/push        ja
 *
 * Source: translate.verbaria.org/my-proj&#64;main
 *
 * Co-authored-by: Alice &lt;alice&#64;example.org&gt;
 * Co-authored-by: Bob &lt;bob&#64;example.org&gt;
 * </pre>
 */
public final class LockChangelog {

    /** Output rendering: a git commit message, or a Markdown changelog. */
    public enum Format {
        GIT_COMMIT, MARKDOWN;

        public static Format parse(String value) {
            if (value == null) {
                return GIT_COMMIT;
            }
            switch (value.trim().toLowerCase()) {
                case "git-commit":
                case "commit":
                    return GIT_COMMIT;
                case "markdown":
                case "md":
                    return MARKDOWN;
                default:
                    throw new IllegalArgumentException(
                            "Unknown format: " + value
                                    + " (expected git-commit or markdown)");
            }
        }
    }

    /** docId -> sorted locales, for each change category. */
    private final Map<String, TreeSet<String>> updated = new TreeMap<>();
    private final Map<String, TreeSet<String>> added = new TreeMap<>();
    private final Map<String, TreeSet<String>> removed = new TreeMap<>();
    /** docIds whose source content changed (independent of translations). */
    private final TreeSet<String> sourceUpdated = new TreeSet<>();
    private final TreeSet<String> allLocales = new TreeSet<>();
    /** translators of added/updated entries -> Co-authored-by trailers. */
    private final TreeSet<String> coAuthors = new TreeSet<>();
    private final VerbariaLock newLock;

    private LockChangelog(VerbariaLock oldLock, VerbariaLock newLock) {
        this.newLock = newLock == null ? new VerbariaLock() : newLock;
        diff(oldLock == null ? new VerbariaLock() : oldLock, this.newLock);
    }

    public static LockChangelog between(VerbariaLock oldLock,
            VerbariaLock newLock) {
        return new LockChangelog(oldLock, newLock);
    }

    /** Convenience: diff two locks and render the commit message. */
    public static String render(VerbariaLock oldLock, VerbariaLock newLock) {
        return between(oldLock, newLock).render();
    }

    /** Convenience: diff two locks and render in the requested format. */
    public static String render(VerbariaLock oldLock, VerbariaLock newLock,
            Format format) {
        return between(oldLock, newLock).render(format);
    }

    public boolean isEmpty() {
        return updated.isEmpty() && added.isEmpty() && removed.isEmpty()
                && sourceUpdated.isEmpty();
    }

    /** Renders the diff in the requested format ({@code ""} when empty). */
    public String render(Format format) {
        if (isEmpty()) {
            return "";
        }
        return format == Format.MARKDOWN ? renderMarkdown() : render();
    }

    private void diff(VerbariaLock oldLock, VerbariaLock newLock) {
        TreeSet<String> docIds = new TreeSet<>();
        docIds.addAll(oldLock.getDocuments().keySet());
        docIds.addAll(newLock.getDocuments().keySet());

        for (String docId : docIds) {
            Map<String, TranslationLock> oldT = translations(oldLock, docId);
            Map<String, TranslationLock> newT = translations(newLock, docId);

            TreeSet<String> locales = new TreeSet<>();
            locales.addAll(oldT.keySet());
            locales.addAll(newT.keySet());

            for (String locale : locales) {
                TranslationLock before = oldT.get(locale);
                TranslationLock after = newT.get(locale);
                if (before == null && after != null) {
                    record(added, docId, locale, after);
                } else if (before != null && after == null) {
                    record(removed, docId, locale, null);
                } else if (before != null
                        && !Objects.equals(before.getSig(), after.getSig())) {
                    record(updated, docId, locale, after);
                }
            }

            // Source change, by the same content-signature mechanism as
            // translations. Only when both sides carry a signature, so an
            // upgrade from a sig-less lock isn't itself reported as a change.
            SourceLock oldS = source(oldLock, docId);
            SourceLock newS = source(newLock, docId);
            if (oldS != null && oldS.getSig() != null
                    && newS != null && newS.getSig() != null
                    && !oldS.getSig().equals(newS.getSig())) {
                sourceUpdated.add(docId);
            }
        }
    }

    private static SourceLock source(VerbariaLock lock, String docId) {
        DocumentLock doc = lock.getDocuments().get(docId);
        return doc == null ? null : doc.getSource();
    }

    private static Map<String, TranslationLock> translations(VerbariaLock lock,
            String docId) {
        DocumentLock doc = lock.getDocuments().get(docId);
        return doc == null ? Map.of() : doc.getTranslations();
    }

    private void record(Map<String, TreeSet<String>> bucket, String docId,
            String locale, TranslationLock after) {
        bucket.computeIfAbsent(docId, k -> new TreeSet<>()).add(locale);
        allLocales.add(locale);
        if (after != null && after.getTranslators() != null) {
            coAuthors.addAll(after.getTranslators());
        }
    }

    public String render() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(subject()).append("\n\n");
        section(sb, "Updated:", updated);
        section(sb, "Added:", added);
        section(sb, "Removed:", removed);
        sourceSection(sb, "Source updated:", sourceUpdated);

        String source = sourceRef();
        if (source != null) {
            sb.append("\nSource: ").append(source).append('\n');
        }

        if (!coAuthors.isEmpty()) {
            sb.append('\n');
            for (String who : coAuthors) {
                sb.append("Co-authored-by: ").append(who).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Markdown changelog for a PR body. Same diff as the commit message, but
     * GitHub-rendered; contributors are listed as plain text (markdown bodies
     * don't trigger {@code Co-authored-by} attribution &mdash; only the commit
     * does).
     */
    private String renderMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Translation sync\n");
        mdSection(sb, "### Updated", updated);
        mdSection(sb, "### Added", added);
        mdSection(sb, "### Removed", removed);
        if (!sourceUpdated.isEmpty()) {
            sb.append("\n### Source updated\n");
            for (String docId : sourceUpdated) {
                sb.append("- `").append(docId).append("`\n");
            }
        }
        if (!coAuthors.isEmpty()) {
            sb.append("\n**Contributors:** ")
                    .append(String.join(", ", coAuthors)).append('\n');
        }
        String source = sourceRef();
        if (source != null) {
            sb.append("\n<sub>Source: ").append(source).append("</sub>\n");
        }
        return sb.toString();
    }

    /**
     * {@code server/project@version} for the footer, with no double slash when
     * the server URL already ends in one. {@code null} when no server is known.
     */
    private String sourceRef() {
        if (newLock.getServer() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(
                newLock.getServer().replaceAll("/+$", ""));
        if (newLock.getProject() != null) {
            sb.append('/').append(newLock.getProject());
            if (newLock.getProjectVersion() != null) {
                sb.append('@').append(newLock.getProjectVersion());
            }
        }
        return sb.toString();
    }

    private void mdSection(StringBuilder sb, String heading,
            Map<String, TreeSet<String>> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        sb.append('\n').append(heading).append('\n');
        for (Map.Entry<String, TreeSet<String>> e : bucket.entrySet()) {
            sb.append("- `").append(e.getKey()).append("` — ")
                    .append(String.join(", ", e.getValue())).append('\n');
        }
    }

    /** Conventional-commit subject, summarised and length-capped. */
    private String subject() {
        TreeSet<String> docs = new TreeSet<>();
        docs.addAll(updated.keySet());
        docs.addAll(added.keySet());
        docs.addAll(removed.keySet());
        docs.addAll(sourceUpdated);
        String subject = "chore(i18n): sync " + docs.size()
                + (docs.size() == 1 ? " doc" : " docs");
        if (!allLocales.isEmpty()) {
            subject += " (" + String.join(", ", allLocales) + ")";
        }
        // keep the summary line readable in `git log --oneline`
        if (subject.length() > 72) {
            subject = "chore(i18n): sync " + docs.size() + " docs across "
                    + allLocales.size() + " locales";
        }
        return subject;
    }

    private void section(StringBuilder sb, String heading,
            Map<String, TreeSet<String>> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        sb.append(heading).append('\n');
        int pad = 0;
        for (String docId : bucket.keySet()) {
            pad = Math.max(pad, docId.length());
        }
        for (Map.Entry<String, TreeSet<String>> e : bucket.entrySet()) {
            sb.append("  ").append(padRight(e.getKey(), pad)).append("  ")
                    .append(String.join(", ", e.getValue())).append('\n');
        }
    }

    private void sourceSection(StringBuilder sb, String heading,
            TreeSet<String> docIds) {
        if (docIds.isEmpty()) {
            return;
        }
        sb.append(heading).append('\n');
        for (String docId : docIds) {
            sb.append("  ").append(docId).append('\n');
        }
    }

    private static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    /** The {@code Co-authored-by} identities, for testing/inspection. */
    public List<String> getCoAuthors() {
        return List.copyOf(coAuthors);
    }
}
