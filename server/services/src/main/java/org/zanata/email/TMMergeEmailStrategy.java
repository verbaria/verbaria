/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.email;

import org.zanata.common.ContentState;
import org.zanata.i18n.Messages;
import org.zanata.service.TextFlowCounter;
import org.zanata.service.tm.merge.TMMergeResult;

import java.util.Collections;
import java.util.List;

import kotlin.ranges.IntRange;

/**
 * The HtmlEmailStrategy for TM Merge results.
 *
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
// It might be better pass context/mergeResult to methods which need them, not to constructor
public class TMMergeEmailStrategy extends HtmlEmailStrategy {

    private static final int ONE_HUNDRED = 100;

    // inline styles
    // Mirrors the original Kotlin private 's' object.
    private static final class S {
        // based on class styles:
        static final String BRANDING = """
                color: #03A6D7;
                font-weight: 500;
                letter-spacing: 0.1rem;
                text-transform: uppercase;
                font-size: 3rem;""";
        static final String NO_UNDERLINE = "text-decoration: none;";
        static final String APPROVED = "color: #20718A;";
        static final String TRANSLATED = "color: #62c876;";
        static final String FUZZY = "color: #ffa800;";
        static final String ERROR = "color: red;";
        static final String CONTAINER = "width: 80%; margin-left: auto; margin-right: auto;";
        static final String DARK = "color: #546677;";
        static final String LIGHT = "color: #A2B3BE; font-weight: 500;";
        static final String LOW_WEIGHT = "font-weight: 300;";
        static final String MARGIN_LEFT_0 = "margin-left:0;";
        static final String PAD_RIGHT_20 = "padding-right: 20px;";

        // based on element styles:
        static final String TEXT = "font-family: 'Source Sans Pro', 'Helvetica Neue', Helvetica, Arial, sans-serif;";
        static final String H1 = "color:#20718A; font-weight: 400;";
        static final String H2 = "margin-bottom: 0;";
        static final String H3 = "text-align: left; font-size: 1rem; font-weight: 400; margin-bottom: 0;";
        static final String TABLE = "width: 80%;";
        static final String IN_TABLE = "line-height: 2rem; background-color: #fefefe;";
        static final String TD = "border-bottom: solid 1px #BDD4DC; color: #555;";
        static final String UL = """
                padding-left: 0;
                margin: 0;
                display: inline-flex;
                list-style-type:none;""";

        private S() {
        }
    }

    private final TMMergeEmailContext context;
    private final TMMergeResult mergeResult;

    public TMMergeEmailStrategy(TMMergeEmailContext context, TMMergeResult mergeResult) {
        this.context = context;
        this.mergeResult = mergeResult;
    }

    public TMMergeEmailContext getContext() {
        return context;
    }

    public TMMergeResult getMergeResult() {
        return mergeResult;
    }

    @Override
    public String getSubject(Messages msgs) {
        return msgs.get("email.templates.tm_merge.Results");
    }

    @Override
    public List<String> getReceivedReasons(Messages msgs) {
        return Collections.singletonList(msgs.get("email.templates.tm_merge.TriggeredByYou"));
    }

    @Override
    public EmailAddressBlock getAddresses() {
        return new EmailAddressBlock(null, context.getToAddresses());
    }

    /**
     * Returns a human-readable name for a ContentState, suitable for using in
     * the TM Merge report.
     */
    private static String niceName(ContentState state) {
        return switch (state) {
            case NeedReview -> "Fuzzy";
            default -> state.name();
        };
    }

    /**
     * Returns the inline style which should be used for the section heading
     * corresponding to a ContentState.
     */
    private static String styleFor(ContentState state) {
        return switch (state) {
            case Approved -> S.APPROVED;
            case Translated -> S.TRANSLATED;
            case NeedReview -> S.FUZZY;
            default -> S.ERROR;
        };
    }

    // TODO: kotlinx.html ported as raw HTML
    @Override
    public String renderBody(GeneralEmailContext generalContext, Messages msgs) {
        StringBuilder out = new StringBuilder();
        out.append("<div style=\"").append(attr(S.CONTAINER + S.TEXT)).append("\">");

        out.append("<a href=\"").append(attr(generalContext.serverURL()))
                .append("\" style=\"").append(attr(S.NO_UNDERLINE)).append("\">");
        out.append("<img src=\"http://zanata.org/assets/logo-sm.png\">");
        out.append("<span style=\"").append(attr(S.BRANDING)).append("\">");
        out.append(esc(msgs.get("jsf.Zanata")));
        out.append("</span>");
        out.append("</a>");

        out.append("<h1 style=\"").append(attr(S.H1 + S.LOW_WEIGHT)).append("\">");
        out.append(esc(msgs.get("email.templates.tm_merge.Results")));
        out.append("</h1>");

        out.append("<ul style=\"").append(attr(S.UL)).append("\">");
        out.append("<li style=\"").append(attr(S.DARK + S.MARGIN_LEFT_0 + S.PAD_RIGHT_20)).append("\">");
        out.append(esc(msgs.get("jsf.Project"))).append(": ");
        out.append("<a href=\"").append(attr(context.getProject().url()))
                .append("\" style=\"").append(attr(S.DARK)).append("\">");
        out.append(esc(context.getProject().name()));
        out.append("</a>");
        out.append("</li>");
        out.append("<li style=\"").append(attr(S.DARK + S.MARGIN_LEFT_0 + S.PAD_RIGHT_20)).append("\">");
        out.append(esc(msgs.get("jsf.Version"))).append(": ");
        out.append("<a href=\"").append(attr(context.getVersion().url()))
                .append("\" style=\"").append(attr(S.DARK)).append("\">");
        out.append(esc(context.getVersion().slug()));
        out.append("</a>");
        out.append("</li>");
        out.append("<li style=\"").append(attr(S.DARK + S.MARGIN_LEFT_0)).append("\">");
        out.append(esc(msgs.get("jsf.Locale"))).append(": ");
        out.append("<a style=\"").append(attr(S.DARK)).append("\">");
        out.append(esc(context.getLocale().getId()));
        out.append("</a>");
        out.append("</li>");
        out.append("</ul>");

        out.append("<p style=\"").append(attr(S.DARK)).append("\">");
        out.append(esc(msgs.get("email.templates.tm_merge.MatchRange")));
        out.append(": ");
        out.append("<span style=\"").append(attr(S.LIGHT)).append("\">");
        out.append(esc(msgs.format(
                "email.templates.tm_merge.MatchRangeFromXToY",
                context.getMatchRange().getFirst(),
                context.getMatchRange().getLast())));
        out.append("</span>");
        out.append("</p>");

        // hr is useful for the plain text version generated from this HTML
        out.append("<hr style=\"display:none;\">");

        boolean someResults = false;
        for (ContentState state : mergeResult.getContentStates()) {
            if (mergeResult.noMessagesCounted(state)) continue;
            someResults = true;
            out.append("<h2 style=\"")
                    .append(attr(S.H2 + S.LOW_WEIGHT + styleFor(state))).append("\">");
            out.append(esc(msgs.format("email.templates.tm_merge.CopiedAs", niceName(state))));
            out.append("</h2>");

            for (IntRange range : mergeResult.rangesForContentState(state)) {
                if (mergeResult.noMessagesCounted(state, range)) continue;
                out.append("<h3 style=\"").append(attr(S.H3 + S.LIGHT)).append("\">");
                if (range.getFirst() == ONE_HUNDRED) {
                    out.append(esc(msgs.get("email.templates.tm_merge.100Match")));
                } else {
                    out.append(esc(msgs.format(
                            "email.templates.tm_merge.XToYRangeMatch",
                            range.getFirst(), range.getLast())));
                }
                out.append("</h3>");

                out.append("<table style=\"").append(attr(S.TABLE)).append("\">");
                out.append("<tbody style=\"").append(attr(S.IN_TABLE)).append("\">");
                out.append("<tr style=\"").append(attr(S.IN_TABLE)).append("\">");
                out.append("<td style=\"").append(attr(S.TD + S.IN_TABLE)).append("\">");
                TextFlowCounter ctr = mergeResult.getCounter(state, range);
                out.append(esc(msgs.format(
                        "email.templates.tm_merge.WordsCharsMessages",
                        ctr.getWords(), ctr.getCodePoints(), ctr.getMessages())));
                out.append("</td>");
                out.append("</tr>");
                out.append("</tbody>");
                out.append("</table>");
            }
            // hr is useful for the plain text version generated from this HTML
            out.append("<hr style=\"display:none;\">");
        }
        if (!someResults) {
            out.append("<p>");
            out.append(esc(msgs.get("email.templates.tm_merge.NothingCopied")));
            out.append("</p>");
        }
        out.append("</div>");
        return out.toString();
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String attr(String s) {
        return esc(s);
    }
}
