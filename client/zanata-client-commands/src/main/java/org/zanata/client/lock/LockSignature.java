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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.zanata.client.lock.VerbariaLock.TranslationLock;
import org.zanata.common.ContentState;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.util.HashUtil;

/**
 * Computes the per-(document, locale) signature stored in {@link VerbariaLock}.
 *
 * <p>The Verbaria server no longer emits a translations ETag, so the lock
 * derives its own signature from the returned targets: a hash over the sorted
 * {@code (resId, state, revision)} tuples. Whether a translation "counts"
 * depends on the {@code includeFuzzy} policy:</p>
 * <ul>
 *   <li>default (accepted only) &mdash; {@code Translated} and {@code Approved}
 *       targets, i.e. the signature only moves when accepted content changes;</li>
 *   <li>{@code includeFuzzy} &mdash; also counts {@code NeedReview} targets.</li>
 * </ul>
 */
public final class LockSignature {

    private static final char UNIT = '\u0001';
    private static final char RECORD = '\u0002';

    private LockSignature() {
    }

    /** True if a target in this state contributes to the signature. */
    public static boolean counts(ContentState state, boolean includeFuzzy) {
        if (state == null) {
            return false;
        }
        switch (state) {
            case Approved:
            case Translated:
                return true;
            case NeedReview:
                return includeFuzzy;
            case New:
            case Rejected:
            default:
                return false;
        }
    }

    /**
     * Builds the lock entry for a document+locale from its translation targets,
     * or {@code null} when no target counts (nothing worth recording).
     *
     * @param targets the targets returned for one document and locale
     * @param includeFuzzy whether NeedReview targets count
     */
    public static TranslationLock fromTargets(List<TextFlowTarget> targets,
            boolean includeFuzzy) {
        List<TextFlowTarget> included = new ArrayList<>();
        for (TextFlowTarget t : targets) {
            if (counts(t.getState(), includeFuzzy)) {
                included.add(t);
            }
        }
        if (included.isEmpty()) {
            return null;
        }
        included.sort(Comparator.comparing(TextFlowTarget::getResId,
                Comparator.nullsFirst(Comparator.naturalOrder())));

        StringBuilder sb = new StringBuilder();
        int maxRank = -1;
        ContentState representative = null;
        // sorted + deduped so the lock output (and co-author list) is stable
        TreeSet<String> translators = new TreeSet<>();
        for (TextFlowTarget t : included) {
            sb.append(t.getResId()).append(UNIT)
                    .append(t.getState()).append(UNIT)
                    .append(t.getRevision()).append(RECORD);
            int rank = rank(t.getState());
            if (rank > maxRank) {
                maxRank = rank;
                representative = t.getState();
            }
            String who = formatPerson(t.getTranslator());
            if (who != null) {
                translators.add(who);
            }
        }
        String sig = HashUtil.generateHash(sb.toString());
        String state = representative == null ? null : representative.name();
        List<String> people =
                translators.isEmpty() ? null : new ArrayList<>(translators);
        return new TranslationLock(sig, state, included.size(), people);
    }

    /** Renders a translator as {@code "Name <email>"}, or {@code null}. */
    public static String formatPerson(Person person) {
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

    /** Orders states from least to most complete, for the display label. */
    private static int rank(ContentState state) {
        if (state == null) {
            return 0;
        }
        switch (state) {
            case Approved:
                return 4;
            case Translated:
                return 3;
            case NeedReview:
                return 2;
            case Rejected:
                return 1;
            case New:
            default:
                return 0;
        }
    }
}
