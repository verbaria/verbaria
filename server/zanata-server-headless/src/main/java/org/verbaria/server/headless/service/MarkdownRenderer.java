package org.verbaria.server.headless.service;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Renders admin-edited Markdown to HTML safe for embedding in the homepage.
 *
 * <p>Uses CommonMark for the Markdown → HTML conversion, then runs the result
 * through an OWASP HTML sanitiser. The sanitiser policy mirrors the
 * "user-content" allow-list used by the legacy {@code CommonMarkRenderer} in
 * the WildFly stack: standard text formatting, headings, lists, code, tables,
 * images and links. Script tags, inline event handlers and
 * {@code javascript:} URLs are stripped.</p>
 */
@Component
public class MarkdownRenderer {

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer html = HtmlRenderer.builder().build();

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowCommonBlockElements()
            .allowCommonInlineFormattingElements()
            .allowStandardUrlProtocols()
            .allowElements("a", "img", "h1", "h2", "h3", "h4", "h5", "h6",
                    "p", "ul", "ol", "li", "blockquote", "pre", "code",
                    "table", "thead", "tbody", "tr", "th", "td", "hr", "br",
                    "span", "div")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("class").globally()
            .toFactory();

    /** Markdown → sanitised HTML. Empty / null input → empty string. */
    public String render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node parsed = parser.parse(markdown);
        String unsafe = html.render(parsed);
        return POLICY.sanitize(unsafe);
    }
}
