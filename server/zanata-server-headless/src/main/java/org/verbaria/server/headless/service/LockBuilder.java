package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.zanata.common.ContentState;
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
        for (TextFlowTarget t : included) {
            // The signature folds in resId, state and revision — all the
            // changelog needs to detect a change. The translators are NOT stored
            // here: the server derives them from its own history at changelog
            // time (using the previous lock as the baseline).
            sb.append(t.getResId()).append(UNIT)
                    .append(t.getState()).append(UNIT)
                    .append(t.getRevision()).append(RECORD);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sig", HashUtil.generateHash(sb.toString()));
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
}
