/*
 * Copyright Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.service.tm.merge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.zanata.common.ContentState;
import org.zanata.service.TextFlowCounter;
import org.zanata.service.TranslationCounter;

import kotlin.ranges.IntRange;

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public class TMMergeResult implements TranslationCounter {

    private static final List<ContentState> CONTENT_STATES = List.of(
            ContentState.Approved,
            ContentState.Translated,
            ContentState.NeedReview,
            ContentState.Rejected,
            ContentState.New);

    private final Map<ContentState, List<IntRange>> bandDefs;
    private final Map<BandKey, MutableTextFlowCounter> bandCounters =
            new HashMap<>();

    public TMMergeResult(Map<ContentState, List<IntRange>> bandDefs) {
        this.bandDefs = bandDefs;
        // for each defined ContentState/IntRange pair, we hold a Counter
        for (Map.Entry<ContentState, List<IntRange>> entry : bandDefs.entrySet()) {
            for (IntRange r : entry.getValue()) {
                bandCounters.put(new BandKey(entry.getKey(), r),
                        new MutableTextFlowCounter());
            }
        }
    }

    /**
     * Returns all ContentStates in the order we want to report them
     */
    public List<ContentState> getContentStates() {
        return CONTENT_STATES;
    }

    /**
     * Returns a list of IntRanges which together cover 0 to 100 for the specified ContentState.
     */
    public List<IntRange> rangesForContentState(ContentState state) {
        List<IntRange> ranges = bandDefs.get(state);
        Objects.requireNonNull(ranges);
        return ranges;
    }

    /**
     * Returns true if the counter for (state, range) has counted zero messages.
     */
    public boolean noMessagesCounted(ContentState state, IntRange range) {
        MutableTextFlowCounter counter = bandCounters.get(new BandKey(state, range));
        return counter != null && counter.messages == 0L;
    }

    /**
     * Returns true if and only if all the counters for 'state' have counted zero messages.
     */
    public boolean noMessagesCounted(ContentState state) {
        for (IntRange range : rangesForContentState(state)) {
            MutableTextFlowCounter counter = bandCounters.get(new BandKey(state, range));
            if (counter != null && counter.messages != 0L) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a read-only counter for the specified (ContentState, IntRange)
     */
    public TextFlowCounter getCounter(ContentState state, IntRange range) {
        MutableTextFlowCounter counter =
                bandCounters.get(new BandKey(state, range));
        Objects.requireNonNull(counter);
        return counter;
    }

    @Override
    public void count(ContentState state, int score, long chars, long words,
            long messages) {
        assert messages >= 1;
        List<IntRange> ranges = bandDefs.get(state);
        if (ranges == null) {
            throw new RuntimeException("missing bands for " + state);
        }
        IntRange matched = null;
        for (IntRange range : ranges) {
            if (range.contains(score)) {
                matched = range;
                break;
            }
        }
        if (matched == null) {
            throw new RuntimeException("missing band for " + score);
        }
        BandKey counterKey = new BandKey(state, matched);
        MutableTextFlowCounter counter = bandCounters.get(counterKey);
        if (counter == null) {
            throw new RuntimeException("missing counter for " + counterKey);
        }
        counter.codePoints += chars;
        counter.words += words;
        counter.messages += messages;
    }

    private record BandKey(ContentState state, IntRange range) {
    }

    private static final class MutableTextFlowCounter implements TextFlowCounter {
        long codePoints;
        long words;
        long messages;

        @Override
        public long getCodePoints() {
            return codePoints;
        }

        @Override
        public long getWords() {
            return words;
        }

        @Override
        public long getMessages() {
            return messages;
        }
    }
}
