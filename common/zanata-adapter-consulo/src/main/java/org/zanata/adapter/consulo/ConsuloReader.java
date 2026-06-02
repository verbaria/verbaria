package org.zanata.adapter.consulo;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

public final class ConsuloReader {

    public TranslationsResource extractTarget(InputStream in) {
        TranslationsResource res = new TranslationsResource();
        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (!(root instanceof Map<?, ?> top)) return res;
        List<TextFlowTarget> targets = new ArrayList<>(top.size());
        for (Map.Entry<?, ?> entry : top.entrySet()) {
            String key = String.valueOf(entry.getKey());
            String text = textOf(entry.getValue());
            if (text == null) continue;
            TextFlowTarget t = new TextFlowTarget(key);
            t.setContents(text);
            t.setState(ContentState.Translated);
            targets.add(t);
        }
        res.getTextFlowTargets().addAll(targets);
        return res;
    }

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
            String comment = commentOf(entry.getValue());
            if (comment != null && !comment.isEmpty()) {
                tf.getExtensions(true).add(new SimpleComment(comment));
            }
            List<String> names = listOf(entry.getValue(), "names");
            List<String> types = listOf(entry.getValue(), "types");
            if ((names != null && !names.isEmpty())
                    || (types != null && !types.isEmpty())) {
                ConsuloSubFile params = new ConsuloSubFile();
                params.setNames(names);
                params.setTypes(types);
                tf.getExtensions(true).add(params);
            }
            flows.add(tf);
        }
        resource.getTextFlows().addAll(flows);
        return resource;
    }

    private static String commentOf(Object value) {
        if (value instanceof Map<?, ?> m) {
            Object c = m.get("comment");
            return c == null ? null : String.valueOf(c);
        }
        return null;
    }

    private static List<String> listOf(Object value, String field) {
        if (value instanceof Map<?, ?> m && m.get(field) instanceof List<?> l) {
            List<String> out = new ArrayList<>(l.size());
            for (Object o : l) {
                out.add(o == null ? null : String.valueOf(o));
            }
            return out;
        }
        return null;
    }

    private static String textOf(Object value) {
        if (value == null) return null;
        if (value instanceof String s) return s;
        if (value instanceof Map<?, ?> m) {
            Object t = m.get("text");
            return t == null ? null : String.valueOf(t);
        }
        return null;
    }

    public static Map<String, String> flatten(Map<String, Object> top) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : top.entrySet()) {
            String t = textOf(e.getValue());
            if (t != null) out.put(e.getKey(), t);
        }
        return out;
    }
}
