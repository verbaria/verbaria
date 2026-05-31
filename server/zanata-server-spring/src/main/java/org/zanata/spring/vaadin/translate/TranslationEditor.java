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

    private static final String SQUIGGLE_SVG =
            "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' "
            + "viewBox='0 0 6 3' width='6' height='3'>"
            + "<path d='M0 2 Q1.5 0 3 2 T6 2' stroke='%23d11' "
            + "stroke-width='0.8' fill='none'/></svg>";

    private static final String SQUIGGLE_CSS =
            ".ace_marker-layer .red{"
            + "background-color:transparent !important;"
            + "background-image:url(\"" + SQUIGGLE_SVG + "\") !important;"
            + "background-repeat:repeat-x !important;"
            + "background-position:bottom !important;"
            + "background-size:6px 3px !important;"
            + "position:absolute !important;"
            + "}";

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
            addMarkerAtSelection(start[0], start[1], end[0], end[1],
                    AceMarkerColor.red, issue.ruleId());
        }
    }

    private void installSquiggleStyles() {
        getElement().executeJs(
                "if (this.shadowRoot && !this._verbariaSquiggle) {"
                + "const s = new CSSStyleSheet();"
                + "s.replaceSync($0);"
                + "this.shadowRoot.adoptedStyleSheets = "
                + "[...this.shadowRoot.adoptedStyleSheets, s];"
                + "this._verbariaSquiggle = true;"
                + "}", SQUIGGLE_CSS);
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
