package org.verbaria.server.headless.adapter.yaml;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.Yaml;

/**
 * Emits Consulo-style localization YAML — the same shape Consulo's
 * {@code LocalizeGenerator} expects:
 *
 * <pre>
 * button.add.class:
 *     text: Add Class
 * </pre>
 *
 * Keys are written in the iteration order of the input map (use a
 * {@link LinkedHashMap} to preserve source order). Entries with a null
 * or blank value are skipped — the platform's fallback chain pulls
 * those from the en_US bundle at runtime, so emitting an empty
 * {@code text:} would only confuse translators reading the file.
 */
public final class YamlWriter {

    public void write(Writer out, Map<String, String> entries) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(FlowStyle.BLOCK);
        opts.setLineBreak(LineBreak.UNIX);
        opts.setIndent(2);
        opts.setIndicatorIndent(0);
        opts.setSplitLines(false);
        // Match Consulo's hand-edited files: 4-space indent for the
        // `text:` line under each top-level key.
        opts.setIndent(4);

        Map<String, Map<String, String>> doc = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            Map<String, String> body = new LinkedHashMap<>(1);
            body.put("text", e.getValue());
            doc.put(e.getKey(), body);
        }

        new Yaml(opts).dump(doc, out);
    }
}
