package org.zanata.adapter.consulo;

import java.io.IOException;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.Yaml;

public final class ConsuloWriter {

    public void write(Writer out, Map<String, String> entries) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(FlowStyle.BLOCK);
        opts.setLineBreak(LineBreak.UNIX);
        opts.setIndicatorIndent(0);
        opts.setSplitLines(false);
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
