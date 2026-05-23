/*
 * Copyright 2013, 2015, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.adapter;

import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @see #separate
 * @author David Mason, <a href="mailto:damason@redhat.com">damason@redhat.com</a>
 * @author Sean Flanigan, <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public final class TranslatableSeparator {

    private TranslatableSeparator() {
    }

    public static final class SplitString {
        /** leading (non-translatable) portion */
        private final String pre;
        /** translatable portion of string */
        private final String str;
        /** trailing (non-translatable) portion */
        private final String suf;

        public SplitString(String pre, String str, String suf) {
            this.pre = pre;
            this.str = str;
            this.suf = suf;
        }

        public String getPre() {
            return pre;
        }

        public String getStr() {
            return str;
        }

        public String getSuf() {
            return suf;
        }

        public String pre() {
            return pre;
        }

        public String str() {
            return str;
        }

        public String suf() {
            return suf;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SplitString that)) return false;
            return java.util.Objects.equals(pre, that.pre)
                    && java.util.Objects.equals(str, that.str)
                    && java.util.Objects.equals(suf, that.suf);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(pre, str, suf);
        }

        @Override
        public String toString() {
            return "SplitString(pre=" + pre + ", str=" + str + ", suf=" + suf + ")";
        }
    }

    /**
     * Separates leading and trailing non-translatable portions of a string from
     * the translatable portion.
     *
     * <p>The leading portion maps to 'pre', the trailing non-translatable portion
     * maps to 'suf' and the translatable portion maps to 'str'.
     */
    public static SplitString separate(String s) {
        Function<SplitString, SplitString> stripAllNonTranslatable =
                ((Function<SplitString, SplitString>) TranslatableSeparator::stripLeadingWhitespace)
                        .andThen(TranslatableSeparator::stripTrailingWhitespace)
                        .andThen(TranslatableSeparator::stripLeadingEmptyTags)
                        .andThen(TranslatableSeparator::stripTrailingEmptyTag)
                        .andThen(TranslatableSeparator::stripWrappingTags)
                        .andThen(TranslatableSeparator::stripLeadingEmptyPairedTags)
                        .andThen(TranslatableSeparator::stripTrailingEmptyPairedTags);
        SplitString preStripped = new SplitString("", s, "");
        SplitString stripped = stripAllNonTranslatable.apply(preStripped);
        while (!preStripped.equals(stripped)) {
            preStripped = stripped;
            stripped = stripAllNonTranslatable.apply(preStripped);
        }
        return stripped;
    }

    static SplitString stripLeadingWhitespace(SplitString it) {
        String leadingWhitespace = "(?ms)^(\\s*)(.*)$";
        Matcher matcher = Pattern.compile(leadingWhitespace).matcher(it.str());
        if (matcher.matches()) {
            return new SplitString(
                    it.pre() + matcher.group(1),
                    matcher.group(2),
                    it.suf());
        }
        return it;
    }

    // Does not treat whitespace-only string as having trailing whitespace
    static SplitString stripTrailingWhitespace(SplitString it) {
        String trailingWhitespace = "(?ms)^(.*[^\\s])(\\s*)$";
        Matcher matcher = Pattern.compile(trailingWhitespace).matcher(it.str());
        if (matcher.matches()) {
            return new SplitString(
                    it.pre(),
                    matcher.group(1),
                    matcher.group(2) + it.suf());
        }
        return it;
    }

    static SplitString stripLeadingEmptyTags(SplitString it) {
        String leadingStandaloneTags = "(?ms)^((?:<[^/>]*/ ?>)*)(.*)$";
        Matcher matcher = Pattern.compile(leadingStandaloneTags).matcher(it.str());
        if (matcher.matches()) {
            return new SplitString(
                    it.pre() + matcher.group(1),
                    matcher.group(2),
                    it.suf());
        }
        return it;
    }

    static SplitString stripTrailingEmptyTag(SplitString it) {
        String trailingStandaloneTag = "(?ms)^(.*)(<[^/>]*/\\s*>)$";
        Matcher matcher = Pattern.compile(trailingStandaloneTag).matcher(it.str());
        if (matcher.matches()) {
            return new SplitString(
                    it.pre(),
                    matcher.group(1),
                    matcher.group(2) + it.suf());
        }
        return it;
    }

    static SplitString stripWrappingTags(SplitString it) {
        String wrappingTags = "(?ms)^(<([^/>]*)\\s*>)(.*)(</\\2\\s*>)$";
        Matcher matcher = Pattern.compile(wrappingTags).matcher(it.str());
        if (matcher.matches()) {
            return new SplitString(
                    it.pre() + matcher.group(1),
                    matcher.group(3),
                    matcher.group(4) + it.suf());
        }
        return it;
    }

    static SplitString stripLeadingEmptyPairedTags(SplitString it) {
        String leadingPairedTags = "(?ms)^(<([^/>]*)\\s*>(.*)</\\2\\s*>)(.*)$";
        Matcher matcher = Pattern.compile(leadingPairedTags).matcher(it.str());
        if (matcher.matches()) {
            String tagContents = matcher.group(3);
            String cleanedContents = separate(tagContents).str();
            if (cleanedContents.isEmpty()) {
                return new SplitString(
                        it.pre() + matcher.group(1),
                        matcher.group(4),
                        it.suf());
            }
        }
        return it;
    }

    static SplitString stripTrailingEmptyPairedTags(SplitString it) {
        String trailingPairedTags = "(?ms)^(.*?)(<([^/>]*)\\s*>(.*)</\\3\\s*>)$";
        Matcher matcher = Pattern.compile(trailingPairedTags).matcher(it.str());
        if (matcher.matches()) {
            String tagContents = matcher.group(4);
            String cleanedContents = separate(tagContents).str();
            if (cleanedContents.isEmpty()) {
                return new SplitString(
                        it.pre(),
                        matcher.group(1),
                        matcher.group(2) + it.suf());
            }
        }
        return it;
    }
}
