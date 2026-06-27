package org.zanata.adapter.consulo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

@Component
public final class ConsuloDocumentLayout implements DocumentLayout {

    private static final String LIB = "LOCALIZE-LIB/";

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Consulo);
    }

    @Override
    public String fileExtension() {
        return ".yaml";
    }

    @Override
    public String contentType() {
        return "application/x-yaml";
    }

    @Override
    public List<String> scanPatterns() {
        return List.of("**/" + LIB + "**");
    }

    @Override
    public Optional<PathDoc> classify(String relativePath) {
        String norm = relativePath.replace('\\', '/');
        int idx = norm.indexOf(LIB);
        if (idx < 0) {
            return Optional.empty();
        }
        String tail = norm.substring(idx + LIB.length());
        int slash = tail.indexOf('/');
        if (slash < 0) {
            return Optional.empty();
        }
        String localeId = tail.substring(0, slash).replace('_', '-');
        String afterLocale = tail.substring(slash + 1);
        int next = afterLocale.indexOf('/');
        String docId = next < 0 ? stripExtension(afterLocale)
                : afterLocale.substring(0, next);
        return Optional.of(new PathDoc(docId, localeId));
    }

    @Override
    public String outputPath(String docId, String localeId) {
        return localeId.replace('-', '_') + "/" + docId + ".yaml";
    }

    @Override
    public Resource readSource(String docId, byte[] content) {
        return new ConsuloReader()
                .extractTemplate(docId, new ByteArrayInputStream(content));
    }

    @Override
    public Resource readSource(String docId, Map<String, byte[]> files) {
        Resource resource = new Resource(docId);
        resource.setLang(LocaleId.EN_US);
        String subMarker = "/" + docId + "/";
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey().replace('\\', '/');
            byte[] bytes = e.getValue();
            if (isAnchor(path, docId)) {
                Resource anchor = new ConsuloReader().extractTemplate(docId,
                        new ByteArrayInputStream(bytes));
                resource.getTextFlows().addAll(anchor.getTextFlows());
            } else {
                int idx = path.indexOf(subMarker);
                String rel = idx >= 0
                        ? path.substring(idx + subMarker.length())
                        : path.substring(path.lastIndexOf('/') + 1);
                String ext = "";
                String stem = rel;
                int dot = rel.lastIndexOf('.');
                if (dot > rel.lastIndexOf('/')) {
                    ext = rel.substring(dot + 1);
                    stem = rel.substring(0, dot);
                }
                String key = stem.replace('/', '.');
                TextFlow tf = new TextFlow(key, LocaleId.EN_US,
                        new String(bytes, StandardCharsets.UTF_8));
                tf.getExtensions(true).add(new ConsuloSubFile(ext));
                resource.getTextFlows().add(tf);
            }
        }
        return resource;
    }

    @Override
    public Map<String, byte[]> writeSourceFiles(Resource source,
            String sourceLocaleId) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        Map<String, ConsuloWriter.Entry> yamlEntries = new LinkedHashMap<>();
        String base = sourceLocaleId.replace('-', '_') + "/"
                + source.getName();
        for (TextFlow tf : source.getTextFlows()) {
            if (tf.getContents() == null || tf.getContents().isEmpty()) {
                continue;
            }
            String content = tf.getContents().get(0);
            ConsuloSubFile sub = tf.getExtensions() == null ? null
                    : tf.getExtensions().findByType(ConsuloSubFile.class);
            if (sub != null && sub.getExtension() != null
                    && !sub.getExtension().isEmpty()) {
                String rel = humanKey(tf).replace('.', '/') + "."
                        + sub.getExtension();
                out.put(base + "/" + rel,
                        content.getBytes(StandardCharsets.UTF_8));
            } else {
                yamlEntries.put(humanKey(tf), entry(tf, content));
            }
        }
        out.put(base + ".yaml", render(yamlEntries));
        return out;
    }

    private static boolean isAnchor(String path, String docId) {
        return path.endsWith("/" + docId + ".yaml")
                || path.equals(docId + ".yaml")
                || path.endsWith("/" + docId + ".yml")
                || path.equals(docId + ".yml");
    }

    @Override
    public TranslationsResource readTranslation(byte[] content) {
        return new ConsuloReader().extractTarget(new ByteArrayInputStream(content));
    }

    @Override
    public byte[] writeSource(Resource source) throws IOException {
        Map<String, ConsuloWriter.Entry> entries = new LinkedHashMap<>();
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
        Map<String, ConsuloWriter.Entry> entries = new LinkedHashMap<>();
        for (TextFlow tf : source.getTextFlows()) {
            String content = byResId.get(tf.getId());
            if (content == null) {
                continue;
            }
            entries.put(humanKey(tf), entry(tf, content));
        }
        return render(entries);
    }

    private static ConsuloWriter.Entry entry(TextFlow tf, String content) {
        ConsuloSubFile consulo = tf.getExtensions() == null ? null
                : tf.getExtensions().findByType(ConsuloSubFile.class);
        List<String> names = consulo == null ? null : consulo.getParamNames();
        List<String> types = consulo == null ? null : consulo.getParamTypes();
        return new ConsuloWriter.Entry(content, names, types);
    }

    private static byte[] render(Map<String, ConsuloWriter.Entry> entries)
            throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new ConsuloWriter().writeEntries(w, entries);
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

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf('/');
        return dot > slash ? name.substring(0, dot) : name;
    }
}
