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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.zanata.common.ContentState;

import kotlin.ranges.IntRange;

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public final class TMBandDefs {

    private static final IntRange EXACTLY_100 = new IntRange(100, 100);
    private static final IntRange ZERO_TO_99 = new IntRange(0, 99);
    private static final List<IntRange> FULL_RANGE =
            List.of(new IntRange(0, 100));

    private final Map<ContentState, List<IntRange>> map;

    public TMBandDefs(Map<ContentState, List<IntRange>> map) {
        this.map = map;
    }

    public Map<ContentState, List<IntRange>> getMap() {
        return map;
    }

    /**
     * Returns a normalised list of numbers, starting from 100 down to 0
     *
     * @param origBands a list of numbers between 0 and 100
     */
    private static List<Integer> normaliseLowerBands(List<Integer> origBands) {
        if (origBands.isEmpty()) {
            return Arrays.asList(100, 0);
        }
        for (Integer band : origBands) {
            assert band >= 0;
            assert band <= 100;
        }
        List<Integer> bands = new ArrayList<>(origBands);
        bands.sort(Comparator.reverseOrder());
        if (bands.get(0) < 100) {
            bands.add(0, 100);
        }
        if (bands.get(bands.size() - 1) > 0) {
            bands.add(0);
        }
        return bands;
    }

    /**
     * Parses a string containing a list of integers (between 0 and 100)
     * and returns a descending list of IntRanges which together cover exactly
     * 0 to 100.
     */
    public static List<IntRange> parseBands(String bandConfig) {
        List<Integer> rawBands = new ArrayList<>();
        for (String part : bandConfig.split("[, ]+")) {
            if (!part.isEmpty()) {
                rawBands.add(Integer.parseInt(part));
            }
        }
        List<Integer> bands = normaliseLowerBands(rawBands);
        int previous = 101;
        List<IntRange> ranges = new ArrayList<>();
        for (int lower : bands) {
            ranges.add(new IntRange(lower, previous - 1));
            previous = lower;
        }
        return ranges;
    }

    /**
     * Creates a full set of band definitions, covering each ContentState, and with a list
     * of IntRanges for each ContentState which cover scores between 0 and 100.
     *
     * @param fuzzyBands a descending list of IntRanges for Fuzzy copies which together
     * must cover exactly 0 to 100.
     */
    public static Map<ContentState, List<IntRange>> createTMBands(
            List<IntRange> fuzzyBands) {
        Map<ContentState, List<IntRange>> result =
                new EnumMap<>(ContentState.class);
        result.put(ContentState.Approved, List.of(EXACTLY_100, ZERO_TO_99));
        result.put(ContentState.Translated, List.of(EXACTLY_100, ZERO_TO_99));
        result.put(ContentState.NeedReview, fuzzyBands);
        result.put(ContentState.Rejected, FULL_RANGE);
        result.put(ContentState.New, FULL_RANGE);
        return result;
    }
}
