package org.zanata.spring.vaadin.translate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMarkerColor;
import de.f0rce.ace.enums.AceMode;
import org.springframework.scheduling.TaskScheduler;
import org.zanata.spring.validation.LanguageValidator;
import org.zanata.spring.validation.ValidationIssue;

public class TranslationEditor extends AceEditor {

    private static final Duration DEBOUNCE = Duration.ofMillis(500);

    // Zigzag (squiggle) underline drawn with two diagonal gradients offset by
    // half a tile, so they alternate into a /\/\ wave at the bottom of the
    // marker. Gradients render reliably (unlike a CSS mask here) AND accept an
    // Aura CSS variable, so the colour follows the theme. Replaces the lib
    // theme's solid marker fill. Red = syntax errors, orange = warnings.
    private static final String SQUIGGLE_CSS =
            squiggle("red", "var(--aura-red)")          // errors: red
            + squiggle("orange", "var(--aura-orange)"); // warnings: orange

    private static String squiggle(String markerClass, String c) {
        // Two corner triangles per tile (bottom-left via 45deg, bottom-right via
        // -45deg) leave an upward gap in the middle, so the coloured bottom edge
        // reads as a /\/\ zigzag wave.
        return ".ace_marker-layer ." + markerClass + "{"
                + "background-color:transparent !important;"
                + "background-image:"
                + "linear-gradient(45deg," + c + " 25%,transparent 25%),"
                + "linear-gradient(-45deg," + c + " 25%,transparent 25%)"
                + " !important;"
                + "background-size:4px 3px !important;"
                + "background-position:left bottom !important;"
                + "background-repeat:repeat-x !important;"
                + "position:absolute !important;"
                + "}";
    }

    private final LanguageValidator validator;
    private final TaskScheduler scheduler;
    private final Locale locale;
    private final AtomicReference<ScheduledFuture<?>> pending = new AtomicReference<>();

    public TranslationEditor(LanguageValidator validator,
                             TaskScheduler scheduler,
                             Locale locale) {
        this.validator = validator;
        this.scheduler = scheduler;
        this.locale = locale;
        configure();
        addAceChangedListener(ev -> scheduleValidation(
                ev.getValue() == null ? "" : ev.getValue(), UI.getCurrent()));
    }

    /**
     * Switch syntax highlighting to suit a file extension (e.g. a consulo raw
     * sub-file). Unknown/empty extensions fall back to plain text. Safe to call
     * live when a reviewer changes the extension.
     */
    public void setModeForFileExtension(String ext) {
        setMode(modeForExtension(ext));
    }

    private static AceMode modeForExtension(String ext) {
        if (ext == null) {
            return AceMode.text;
        }
        String e = ext.trim().toLowerCase(Locale.ROOT);
        if (e.startsWith(".")) {
            e = e.substring(1);
        }
        return switch (e) {
            case "html", "htm", "xhtml" -> AceMode.html;      // consulo colour-scheme files are XML
            case "xml", "colorpage", "colorscheme", "svg" -> AceMode.xml;
            case "properties" -> AceMode.properties;
            case "java" -> AceMode.java;
            case "yaml", "yml" -> AceMode.yaml;
            case "json" -> AceMode.json;
            case "css" -> AceMode.css;
            case "js", "mjs" -> AceMode.javascript;
            case "ts" -> AceMode.typescript;
            case "kt", "kts" -> AceMode.kotlin;
            case "groovy", "gradle" -> AceMode.groovy;
            case "scala", "sc" -> AceMode.scala;
            case "py" -> AceMode.python;
            case "sh", "bash" -> AceMode.sh;
            case "sql" -> AceMode.sql;
            case "md", "markdown" -> AceMode.markdown;
            case "toml" -> AceMode.toml;
            case "ini" -> AceMode.ini;
            default -> AceMode.text;
        };
    }

    private void configure() {
        setMode(AceMode.text);
        setShowGutter(false);
        setShowPrintMargin(false);
        setHighlightActiveLine(false);
        setHighlightSelectedWord(false);
        setDisplayIndentGuides(false);
        setShowInvisibles(false);
        setUseWorker(false);
        setWrap(true);
        setSofttabs(true);
        setTabSize(2);
        setAutoComplete(false);
        setLiveAutocompletion(false);
        setEnableSnippets(false);
        setWidthFull();
        setHeight("5rem");
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        installSquiggleStyles();
        if (canValidate()) {
            scheduleValidation(getValue() == null ? "" : getValue(), event.getUI());
        }
    }

    private boolean canValidate() {
        return validator != null && scheduler != null && locale != null;
    }

    private void scheduleValidation(String text, UI ui) {
        if (!canValidate()) {
            return;
        }
        ScheduledFuture<?> prev = pending.get();
        if (prev != null) {
            prev.cancel(false);
        }
        pending.set(scheduler.schedule(
                () -> runValidation(text, ui),
                Instant.now().plus(DEBOUNCE)));
    }

    private void runValidation(String text, UI ui) {
        List<ValidationIssue> issues = validator.validate(text, locale);
        if (ui == null || !ui.isAttached()) {
            return;
        }
        ui.access(() -> applyMarkers(text, issues));
    }

    private void applyMarkers(String text, List<ValidationIssue> issues) {
        removeAllMarkers();
        for (ValidationIssue issue : issues) {
            int[] start = offsetToRowCol(text, issue.offset());
            int[] end = offsetToRowCol(text, issue.offset() + issue.length());
            // Syntax errors are a red squiggle; warnings an orange one.
            AceMarkerColor color =
                    issue.severity() == ValidationIssue.Severity.ERROR
                            ? AceMarkerColor.red : AceMarkerColor.orange;
            addMarkerAtSelection(start[0], start[1], end[0], end[1],
                    color, issue.ruleId());
        }
    }

    private void installSquiggleStyles() {
        // lit-ace renders ACE into the LIGHT DOM (no shadow root) and the lib
        // theme's solid ".ace_marker-layer .orange/.red" rules live in a
        // document-level <style>. So our override must go into document.head
        // (added after the theme + !important) to win. One shared <style>
        // covers every editor on the page.
        getElement().executeJs(
                "let st = document.getElementById('verbaria-squiggle');"
                + "if (!st) {"
                + "  st = document.createElement('style');"
                + "  st.id = 'verbaria-squiggle';"
                + "  document.head.appendChild(st);"
                + "}"
                + "st.textContent = $0;", SQUIGGLE_CSS);
    }

    private static int[] offsetToRowCol(String text, int offset) {
        int row = 0;
        int col = 0;
        int bound = Math.min(offset, text.length());
        for (int i = 0; i < bound; i++) {
            if (text.charAt(i) == '\n') {
                row++;
                col = 0;
            } else {
                col++;
            }
        }
        return new int[]{row, col};
    }
}
