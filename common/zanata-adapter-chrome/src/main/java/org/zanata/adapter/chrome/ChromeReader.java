package org.zanata.adapter.chrome;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

public final class ChromeReader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public Resource extractTemplate(String docId, InputStream in) {
        Resource resource = new Resource(docId);
        resource.setLang(LocaleId.EN_US);

        JsonNode root = read(in);
        if (root == null || !root.isObject()) {
            return resource;
        }

        List<TextFlow> flows = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            JsonNode entry = field.getValue();
            String message = messageOf(entry);
            if (message == null) {
                continue;
            }
            TextFlow tf = new TextFlow(field.getKey(), LocaleId.EN_US, message);
            String description = textOf(entry, "description");
            if (description != null && !description.isEmpty()) {
                tf.getExtensions(true).add(new SimpleComment(description));
            }
            ChromeMessage meta = metaOf(entry);
            if (meta != null && !meta.isEmpty()) {
                tf.getExtensions(true).add(meta);
            }
            flows.add(tf);
        }
        resource.getTextFlows().addAll(flows);
        return resource;
    }

    public TranslationsResource extractTarget(InputStream in) {
        TranslationsResource res = new TranslationsResource();
        JsonNode root = read(in);
        if (root == null || !root.isObject()) {
            return res;
        }
        List<TextFlowTarget> targets = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String message = messageOf(field.getValue());
            if (message == null) {
                continue;
            }
            TextFlowTarget t = new TextFlowTarget(field.getKey());
            t.setContents(message);
            t.setState(ContentState.Translated);
            targets.add(t);
        }
        res.getTextFlowTargets().addAll(targets);
        return res;
    }

    private static JsonNode read(InputStream in) {
        try {
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String messageOf(JsonNode entry) {
        if (entry == null || !entry.isObject()) {
            return null;
        }
        JsonNode message = entry.get("message");
        return message == null || message.isNull() ? null : message.asText();
    }

    private static String textOf(JsonNode entry, String field) {
        JsonNode node = entry.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static ChromeMessage metaOf(JsonNode entry) {
        ChromeMessage meta = new ChromeMessage();
        JsonNode node = entry.get("placeholders");
        if (node != null && node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode body = field.getValue();
                meta.putPlaceholder(field.getKey(), textOf(body, "content"),
                        textOf(body, "example"));
            }
        }
        return meta;
    }
}
