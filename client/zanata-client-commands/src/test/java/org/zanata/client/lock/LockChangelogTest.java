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

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.zanata.client.lock.VerbariaLock.SourceLock;
import org.zanata.client.lock.VerbariaLock.TranslationLock;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

public class LockChangelogTest {

    private static TranslationLock tl(String sig, String... who) {
        List<String> people = who.length == 0 ? null : Arrays.asList(who);
        return new TranslationLock(sig, "Translated", who.length, people);
    }

    private static VerbariaLock lock() {
        VerbariaLock lock = new VerbariaLock();
        lock.setServer("translate.verbaria.org");
        lock.setProject("my-proj");
        lock.setProjectVersion("main");
        return lock;
    }

    private static void src(VerbariaLock lock, String docId, String sig) {
        SourceLock s = new SourceLock(1);
        s.setSig(sig);
        lock.document(docId).setSource(s);
    }

    @Test
    public void sourceOnlyChangeIsReported() {
        VerbariaLock before = lock();
        src(before, "messages", "S1");
        VerbariaLock after = lock();
        src(after, "messages", "S2");

        String msg = LockChangelog.render(before, after);
        assertThat(msg, not(is("")));
        assertThat(msg, containsString("Source updated:"));
        assertThat(msg, containsString("messages"));

        String md = LockChangelog.render(before, after,
                LockChangelog.Format.MARKDOWN);
        assertThat(md, containsString("Source updated"));
        assertThat(md, containsString("messages"));
    }

    @Test
    public void serverTrailingSlashDoesNotProduceDoubleSlash() {
        VerbariaLock before = lock();
        before.document("a").getTranslations().put("de", tl("A"));
        VerbariaLock after = lock();
        after.setServer("https://i.verbaria.org/");
        after.document("a").getTranslations()
                .put("de", tl("B", "Alice <alice@x.org>"));

        String msg = LockChangelog.render(before, after);
        assertThat(msg,
                containsString("Source: https://i.verbaria.org/project/my-proj/version/main"));
        assertThat(msg, not(containsString("org//")));
    }

    @Test
    public void identicalSourceSigProducesEmptyMessage() {
        VerbariaLock before = lock();
        src(before, "messages", "S1");
        VerbariaLock after = lock();
        src(after, "messages", "S1");
        assertThat(LockChangelog.render(before, after), is(""));
    }

    @Test
    public void sourceSigFirstAppearingOnUpgradeIsNotAChange() {
        // An old lock predates source sigs (sig == null); gaining one on the
        // next pull must NOT be reported as a source change.
        VerbariaLock before = lock();
        before.document("messages").setSource(new SourceLock(1)); // no sig
        VerbariaLock after = lock();
        src(after, "messages", "S1");
        assertThat(LockChangelog.render(before, after), is(""));
    }

    @Test
    public void identicalLocksProduceEmptyMessage() {
        VerbariaLock lock = lock();
        lock.document("a").getTranslations().put("de", tl("X"));
        assertThat(LockChangelog.render(lock, lock), is(""));
    }

    @Test
    public void reportsUpdatedAddedRemovedWithCoAuthors() {
        VerbariaLock before = lock();
        before.document("intro").getTranslations().put("de", tl("A"));
        before.document("intro").getTranslations().put("fr", tl("F"));
        before.document("old").getTranslations().put("es", tl("E"));

        VerbariaLock after = lock();
        after.document("intro").getTranslations()
                .put("de", tl("B", "Alice <alice@x.org>"));   // updated
        after.document("intro").getTranslations().put("fr", tl("F")); // same
        after.document("push").getTranslations()
                .put("ja", tl("J", "Bob <bob@x.org>", "Alice <alice@x.org>"));

        String msg = LockChangelog.render(before, after);

        assertThat(msg, containsString("Updated:"));
        assertThat(msg, containsString("intro"));
        assertThat(msg, containsString("Added:"));
        assertThat(msg, containsString("push"));
        assertThat(msg, containsString("Removed:"));
        assertThat(msg, containsString("old"));
        // unchanged locale is not mentioned as a change line
        assertThat(msg, containsString("Source: translate.verbaria.org/project/my-proj/version/main"));
        // co-authors are de-duplicated and present once each
        assertThat(msg, containsString("Co-authored-by: Alice <alice@x.org>"));
        assertThat(msg, containsString("Co-authored-by: Bob <bob@x.org>"));
        assertThat(countOccurrences(msg, "Alice <alice@x.org>"),
                is(1));
    }

    @Test
    public void markdownFormatRendersChangelog() {
        VerbariaLock before = lock();
        before.document("intro").getTranslations().put("de", tl("A"));
        VerbariaLock after = lock();
        after.document("intro").getTranslations()
                .put("de", tl("B", "Alice <alice@x.org>"));

        String md = LockChangelog.render(before, after,
                LockChangelog.Format.MARKDOWN);

        assertThat(md, containsString("## Translation sync"));
        assertThat(md, containsString("### Updated"));
        assertThat(md, containsString("- `intro` — de"));
        assertThat(md, containsString("**Contributors:** Alice <alice@x.org>"));
        // markdown must NOT carry git Co-authored-by trailers
        assertThat(md, not(containsString("Co-authored-by:")));
    }

    @Test
    public void newLockAgainstEmptyTreatsEverythingAsAdded() {
        VerbariaLock after = lock();
        after.document("intro").getTranslations()
                .put("de", tl("B", "Alice <alice@x.org>"));
        String msg = LockChangelog.render(new VerbariaLock(), after);
        assertThat(msg, containsString("Added:"));
        assertThat(msg, containsString("intro"));
        assertThat(msg, not(containsString("Updated:")));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            count++;
            i += needle.length();
        }
        return count;
    }
}
