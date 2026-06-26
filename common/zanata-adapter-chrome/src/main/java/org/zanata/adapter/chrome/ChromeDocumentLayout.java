package org.zanata.adapter.chrome;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.common.ProjectType;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

@Component
public final class ChromeDocumentLayout implements DocumentLayout {

    private static final String LOCALES = "_locales/";
    private static final String FILE = "messages.json";
    private static final String STEM = "messages";

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Chrome);
    }

    @Override
    public String fileExtension() {
        return ".json";
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public java.util.List<String> scanPatterns() {
        return java.util.List.of("**/" + LOCALES + "*/" + FILE);
    }

    @Override
    public Optional<PathDoc> classify(String relativePath) {
        String norm = relativePath.replace('\\', '/');
        if (!norm.endsWith(FILE)) {
            return Optional.empty();
        }
        int idx = norm.indexOf(LOCALES);
        if (idx < 0) {
            return Optional.empty();
        }
        String tail = norm.substring(idx + LOCALES.length());
        int slash = tail.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String localeId = tail.substring(0, slash).replace('_', '-');
        String prefix = idx == 0 ? "" : norm.substring(0, idx - 1);
        String docId = prefix.isEmpty() ? STEM : prefix + "/" + STEM;
        return Optional.of(new PathDoc(docId, localeId));
    }

    @Override
    public String outputPath(String docId, String localeId) {
        String prefix = docId.equals(STEM) ? ""
                : docId.substring(0, docId.length() - ("/" + STEM).length());
        String base = prefix.isEmpty() ? "" : prefix + "/";
        return base + LOCALES + localeId + "/" + FILE;
    }

    @Override
    public Resource readSource(String docId, byte[] content) {
        return new ChromeReader()
                .extractTemplate(docId, new ByteArrayInputStream(content));
    }

    @Override
    public TranslationsResource readTranslation(byte[] content) {
        return new ChromeReader().extractTarget(new ByteArrayInputStream(content));
    }

    @Override
    public byte[] writeSource(Resource source) throws IOException {
        Map<String, ChromeWriter.Entry> entries = new LinkedHashMap<>();
        for (TextFlow tf : source.getTextFlows()) {
            if (tf.getContents() == null || tf.getContents().isEmpty()) {
                continue;
            }
            entries.put(humanKey(tf), entry(tf, tf.getContents().get(0)));
        }
        return render(entries);
    }

    @Override
    public byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException {
        Map<String, String> byResId = new LinkedHashMap<>();
        if (translation.getTextFlowTargets() != null) {
            for (TextFlowTarget t : translation.getTextFlowTargets()) {
                if (t.getContents() != null && !t.getContents().isEmpty()) {
                    String c = t.getContents().get(0);
                    if (c != null && !c.isEmpty()) {
                        byResId.put(t.getResId(), c);
                    }
                }
            }
        }
        Map<String, ChromeWriter.Entry> entries = new LinkedHashMap<>();
        for (TextFlow tf : source.getTextFlows()) {
            String content = byResId.get(tf.getId());
            if (content == null) {
                continue;
            }
            entries.put(humanKey(tf), entry(tf, content));
        }
        return render(entries);
    }

    private static ChromeWriter.Entry entry(TextFlow tf, String content) {
        ChromeMessage meta = tf.getExtensions() == null ? null
                : tf.getExtensions().findByType(ChromeMessage.class);
        SimpleComment comment = tf.getExtensions() == null ? null
                : tf.getExtensions().findByType(SimpleComment.class);
        String description = comment == null ? null : comment.getValue();
        return new ChromeWriter.Entry(content, description, meta);
    }

    private static byte[] render(Map<String, ChromeWriter.Entry> entries)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new ChromeWriter().write(w, entries);
        }
        return out.toByteArray();
    }

    private static String humanKey(TextFlow tf) {
        if (tf.getExtensions() != null) {
            PotEntryHeader h = tf.getExtensions().findByType(PotEntryHeader.class);
            if (h != null && h.getContext() != null && !h.getContext().isEmpty()) {
                return h.getContext();
            }
        }
        return tf.getId();
    }
}
