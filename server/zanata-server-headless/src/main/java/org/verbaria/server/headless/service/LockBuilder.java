package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.zanata.common.ContentState;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.util.HashUtil;

final class LockBuilder {

    private static final char UNIT = '\u0001';
    private static final char RECORD = '\u0002';

    private LockBuilder() {
    }

    static String sourceSignature(Resource source) {
        if (source == null || source.getTextFlows() == null
                || source.getTextFlows().isEmpty()) {
            return null;
        }
        List<TextFlow> flows = new ArrayList<>(source.getTextFlows());
        flows.sort(Comparator.comparing(TextFlow::getId,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        StringBuilder sb = new StringBuilder();
        for (TextFlow tf : flows) {
            sb.append(tf.getId()).append(UNIT);
            if (tf.getContents() != null) {
                for (String c : tf.getContents()) {
                    sb.append(c == null ? "" : c).append(UNIT);
                }
            }
            sb.append(RECORD);
        }
        return HashUtil.generateHash(sb.toString());
    }

    static Map<String, Object> translationLock(TranslationsResource tr,
            boolean includeFuzzy) {
        List<TextFlowTarget> included = new ArrayList<>();
        if (tr.getTextFlowTargets() != null) {
            for (TextFlowTarget t : tr.getTextFlowTargets()) {
                if (counts(t.getState(), includeFuzzy)) {
                    included.add(t);
                }
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
        TreeSet<String> translators = new TreeSet<>();
        for (TextFlowTarget t : included) {
            sb.append(t.getResId()).append(UNIT)
                    .append(t.getState()).append(UNIT)
                    .append(t.getRevision()).append(RECORD);
            int r = rank(t.getState());
            if (r > maxRank) {
                maxRank = r;
                representative = t.getState();
            }
            String who = formatPerson(t.getTranslator());
            if (who != null) {
                translators.add(who);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sig", HashUtil.generateHash(sb.toString()));
        out.put("state", representative == null ? null : representative.name());
        out.put("total", included.size());
        if (!translators.isEmpty()) {
            out.put("translators", new ArrayList<>(translators));
        }
        return out;
    }

    private static boolean counts(ContentState state, boolean includeFuzzy) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case Approved, Translated -> true;
            case NeedReview -> includeFuzzy;
            default -> false;
        };
    }

    private static int rank(ContentState state) {
        if (state == null) {
            return 0;
        }
        return switch (state) {
            case Approved -> 4;
            case Translated -> 3;
            case NeedReview -> 2;
            case Rejected -> 1;
            default -> 0;
        };
    }

    private static String formatPerson(Person person) {
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
