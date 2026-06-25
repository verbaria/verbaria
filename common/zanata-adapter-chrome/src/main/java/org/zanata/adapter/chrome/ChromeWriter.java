package org.zanata.adapter.chrome;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;

public final class ChromeWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Entry(String message, String description, ChromeMessage meta) {

        public Entry(String message) {
            this(message, null, null);
        }
    }

    public void write(Writer out, Map<String, Entry> entries) throws IOException {
        ObjectNode root = MAPPER.createObjectNode();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (entry == null || entry.message() == null) {
                continue;
            }
            ObjectNode body = root.putObject(e.getKey());
            body.put("message", entry.message());
            if (entry.description() != null && !entry.description().isEmpty()) {
                body.put("description", entry.description());
            }
            ChromeMessage meta = entry.meta();
            if (meta == null) {
                continue;
            }
            if (!meta.getPlaceholders().isEmpty()) {
                ObjectNode ph = body.putObject("placeholders");
                for (Map.Entry<String, ChromeMessage.Placeholder> p : meta
                        .getPlaceholders().entrySet()) {
                    ObjectNode slot = ph.putObject(p.getKey());
                    slot.put("content", p.getValue().getContent());
                    if (p.getValue().getExample() != null) {
                        slot.put("example", p.getValue().getExample());
                    }
                }
            }
        }
        MAPPER.writer(prettyPrinter()).writeValue(out, root);
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("    ", "\n");
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(indenter);
        pp.indentArraysWith(indenter);
        return pp;
    }
}
