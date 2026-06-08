package org.zanata.adapter.consulo;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.Yaml;

public final class ConsuloWriter {

    /** One yaml entry: translated text plus the source's placeholder metadata. */
    public record Entry(String text, List<String> names, List<String> types) {}

    public void write(Writer out, Map<String, String> entries) throws IOException {
        Map<String, Entry> rich = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            rich.put(e.getKey(), new Entry(e.getValue(), null, null));
        }
        writeEntries(out, rich);
    }

    public void writeEntries(Writer out, Map<String, Entry> entries) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(FlowStyle.BLOCK);
        opts.setLineBreak(LineBreak.UNIX);
        opts.setIndicatorIndent(0);
        opts.setSplitLines(false);
        opts.setIndent(4);

        Map<String, Map<String, Object>> doc = new LinkedHashMap<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry == null || entry.text() == null || entry.text().isEmpty()) continue;
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", entry.text());
            if (entry.names() != null && !entry.names().isEmpty()) {
                body.put("names", entry.names());
            }
            if (entry.types() != null && !entry.types().isEmpty()) {
                body.put("types", entry.types());
            }
            doc.put(e.getKey(), body);
        }

        new Yaml(new ApostropheSafeRepresenter(opts), opts).dump(doc, out);
    }
}
