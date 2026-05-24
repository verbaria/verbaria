package org.zanata.spring.adapter.yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

/**
 * Parses Consulo-style localization YAMLs:
 *
 * <pre>
 * button.add.class:
 *     text: Add Class
 * are.delete.confirmation.message:
 *     text: Delete {0}?
 * </pre>
 *
 * Each top-level key becomes a {@link TextFlow} whose id is the key
 * (caller is expected to hash it via {@code SourceUploadService.hashTextFlowIds}
 * after extraction so the original key survives in {@code PotEntryHeader.context}).
 *
 * <p>Only the {@code text:} sub-field is read; other sub-fields are
 * preserved on round-trip via the writer when the same TextFlow id is
 * re-emitted but are otherwise ignored. Top-level entries that lack a
 * {@code text} field are skipped (matches the Consulo
 * {@code maven-consulo-plugin:validate-localize} behavior, which only
 * validates {@code text}).</p>
 */
public final class YamlReader {

    /** Reads a Consulo localize YAML stream and emits one TextFlow per entry. */
    public Resource extractTemplate(String docId, InputStream in) {
        Resource resource = new Resource(docId);
        resource.setLang(LocaleId.EN_US);

        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (!(root instanceof Map<?, ?> top)) {
            return resource;
        }

        List<TextFlow> flows = new ArrayList<>(top.size());
        for (Map.Entry<?, ?> entry : top.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String text = textOf(entry.getValue());
            if (text == null) continue;
            TextFlow tf = new TextFlow(key, LocaleId.EN_US, text);
            flows.add(tf);
        }
        resource.getTextFlows().addAll(flows);
        return resource;
    }

    /** Extract the {@code text:} field from an entry value (or use the value as-is if it's a bare string). */
    private static String textOf(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Map<?, ?> m) {
            Object t = m.get("text");
            return t == null ? null : String.valueOf(t);
        }
        return null;
    }

    /** Convenience for callers that already have an ordered map (e.g. from re-parsing on pull). */
    public static Map<String, String> flatten(Map<String, Object> top) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : top.entrySet()) {
            String t = textOf(e.getValue());
            if (t != null) out.put(e.getKey(), t);
        }
        return out;
    }
}
