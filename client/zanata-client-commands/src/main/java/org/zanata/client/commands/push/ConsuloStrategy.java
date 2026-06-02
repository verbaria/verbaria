package org.zanata.client.commands.push;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.Yaml;
import org.zanata.client.commands.push.PushCommand.TranslationResourcesVisitor;
import org.zanata.client.config.LocaleMapping;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import java.nio.file.Path;
import java.io.InputStream;

public class ConsuloStrategy extends AbstractPushStrategy {

    private static final String LOCALIZE_LIB = "LOCALIZE-LIB/";

    public ConsuloStrategy() {
        super(new StringSet("comment;gettext;consulo"), ".yaml");
    }

    @Override
    public void init() {
    }

    @Override
    public Set<String> findDocNames(Path srcDir, ImmutableList<String> includes,
            ImmutableList<String> excludes, boolean useDefaultExclude,
            boolean caseSensitive, boolean excludeLocaleFilenames)
            throws IOException {
        Set<String> docNames = new HashSet<>();
        for (String rel : getSrcFiles(getOpts().getSrcDir(), includes,
                excludes, excludeLocaleFilenames, useDefaultExclude,
                caseSensitive)) {
            String id = docIdFromRel(rel);
            if (id != null) docNames.add(id);
        }
        return docNames;
    }

    @Override
    public Resource loadSrcDoc(Path sourceDir, String docName)
            throws IOException, RuntimeException {
        LocaleId src = new LocaleId(getOpts().getSourceLang());
        Resource doc = new Resource(docName);
        doc.setLang(src);
        Map<String, SrcEntry> entries =
                collectEntries(getOpts().getSrcDir(), docName);
        if (entries.isEmpty()) {
            throw new IOException("No source entries found for docId: " + docName);
        }
        for (Map.Entry<String, SrcEntry> e : entries.entrySet()) {
            TextFlow tf = new TextFlow(e.getKey(), src, e.getValue().text);
            String ext = e.getValue().ext;
            if (ext != null) {
                // Raw sub-file: the key already carries the sub-path; we only
                // need its extension so pull can recreate the exact file. The
                // extension's presence also marks this entry as a raw file.
                tf.getExtensions(true).add(new ConsuloSubFile(ext));
            }
            doc.getTextFlows().add(tf);
        }
        return doc;
    }

    /** A source string plus, for raw sub-files, its file extension. */
    private static final class SrcEntry {
        final String text;
        /** File extension (without dot, may be empty); null for yaml keys. */
        final String ext;
        SrcEntry(String text, String ext) {
            this.text = text;
            this.ext = ext;
        }
    }

    /**
     * Collect every translatable string belonging to {@code docName}: top-level
     * YAML keys (ext null) plus every sub-file under the dir named
     * {@code docName} (sub-file key = rel path inside that dir, extension
     * stripped, {@code /} → {@code .}; ext = the file's extension).
     */
    private Map<String, SrcEntry> collectEntries(Path sourceDir,
            String docName) throws IOException {
        Map<String, SrcEntry> entries = new LinkedHashMap<>();
        for (String rel : getSrcFiles(sourceDir,
                ImmutableList.copyOf(getOpts().getIncludes()),
                ImmutableList.copyOf(getOpts().getExcludes()),
                true, getOpts().getDefaultExcludes(),
                getOpts().getCaseSensitive())) {
            String relNorm = rel.replace('\\', '/');
            int idx = relNorm.indexOf(LOCALIZE_LIB);
            if (idx < 0) continue;
            String tail = relNorm.substring(idx + LOCALIZE_LIB.length());
            int slash = tail.indexOf('/');
            if (slash < 0) continue;
            String afterLocale = tail.substring(slash + 1);
            if (!afterLocale.startsWith(docName)) continue;
            String rest = afterLocale.substring(docName.length());
            Path file = sourceDir.resolve(rel);
            if (rest.endsWith(".yaml") || rest.endsWith(".yml")) {
                if (rest.equals(".yaml") || rest.equals(".yml")) {
                    try (InputStream in = Files.newInputStream(file)) {
                        Object root = new Yaml().load(in);
                        if (root instanceof Map<?, ?> top) {
                            for (Map.Entry<?, ?> e : top.entrySet()) {
                                String key = String.valueOf(e.getKey());
                                String text = textOf(e.getValue());
                                if (text != null) {
                                    entries.put(key, new SrcEntry(text, null));
                                }
                            }
                        }
                    }
                }
            } else if (rest.startsWith("/")) {
                String relInside = rest.substring(1);
                String key = FilenameUtils.removeExtension(relInside)
                        .replace('/', '.');
                String body = Files.readString(file,
                        java.nio.charset.StandardCharsets.UTF_8);
                if (!body.isEmpty()) {
                    // ext "" when the file has none — still marks it raw.
                    entries.put(key, new SrcEntry(body,
                            FilenameUtils.getExtension(relInside)));
                }
            }
        }
        return entries;
    }

    /**
     * DocId from a path: first segment after {@code LOCALIZE-LIB/<locale>/}.
     * For a top-level yaml the extension is stripped; for a path inside a
     * sub-dir the dir name itself is the docId.
     */
    private static String docIdFromRel(String rel) {
        String norm = rel.replace('\\', '/');
        int idx = norm.indexOf(LOCALIZE_LIB);
        if (idx < 0) return null;
        String tail = norm.substring(idx + LOCALIZE_LIB.length());
        int slash = tail.indexOf('/');
        if (slash < 0) return null;
        String afterLocale = tail.substring(slash + 1);
        int nextSlash = afterLocale.indexOf('/');
        if (nextSlash < 0) {
            return FilenameUtils.removeExtension(afterLocale);
        }
        return afterLocale.substring(0, nextSlash);
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

    @Override
    public void visitTranslationResources(String docName, Resource srcDoc,
            TranslationResourcesVisitor callback)
            throws IOException, RuntimeException {
        if (getOpts().getLocaleMapList() == null) return;
        for (LocaleMapping locale : getOpts().getLocaleMapList()) {
            Map<String, String> entries =
                    collectTranslationEntries(docName, locale.getLocale());
            if (entries.isEmpty()) continue;
            TranslationsResource tr = new TranslationsResource();
            for (Map.Entry<String, String> e : entries.entrySet()) {
                String key = e.getKey();
                String resId = looksLikeHexHash(key)
                        ? key : md5Hex(key.toLowerCase(Locale.ROOT));
                TextFlowTarget t = new TextFlowTarget(resId);
                t.setContents(java.util.List.of(e.getValue()));
                t.setState(ContentState.Translated);
                tr.getTextFlowTargets().add(t);
            }
            callback.visit(locale, tr);
        }
    }

    /**
     * Inverse of {@link #collectEntries}: walk the trans-side
     * {@code <trans-dir>/<locale>/<docName>{.yaml,/...}} and build a
     * key→text map.
     */
    private Map<String, String> collectTranslationEntries(String docName, String locale)
            throws IOException {
        File transDir = getOpts().getTransDir().toFile();
        File localeRoot = new File(transDir, locale);
        if (!localeRoot.exists()) {
            File alt = new File(transDir, "LOCALIZE-LIB/" + locale);
            if (alt.exists()) localeRoot = alt;
        }
        if (!localeRoot.exists()) return Map.of();

        Map<String, String> out = new LinkedHashMap<>();
        File yamlFile = caseInsensitiveChild(localeRoot, docName + ".yaml");
        if (yamlFile == null) {
            yamlFile = caseInsensitiveChild(localeRoot, docName + ".yml");
        }
        if (yamlFile != null && yamlFile.isFile()) {
            try (FileInputStream in = new FileInputStream(yamlFile)) {
                Object root = new Yaml().load(in);
                if (root instanceof Map<?, ?> top) {
                    for (Map.Entry<?, ?> e : top.entrySet()) {
                        String key = String.valueOf(e.getKey());
                        String text = textOf(e.getValue());
                        if (text != null && !text.isEmpty()) out.put(key, text);
                    }
                }
            }
        }
        File subDir = caseInsensitiveChild(localeRoot, docName);
        if (subDir != null && subDir.isDirectory()) {
            collectSubFiles(subDir, "", out);
        }
        return out;
    }

    private static void collectSubFiles(File dir, String prefix, Map<String, String> out)
            throws IOException {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File c : children) {
            if (c.isDirectory()) {
                collectSubFiles(c, prefix + c.getName() + ".", out);
            } else {
                String key = prefix + FilenameUtils.removeExtension(c.getName());
                String body = Files.readString(c.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (!body.isEmpty()) out.put(key, body);
            }
        }
    }

    private static File caseInsensitiveChild(File parent, String name) {
        File[] children = parent.listFiles();
        if (children == null) return null;
        for (File c : children) {
            if (c.getName().equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    private static String md5Hex(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return s;
        }
    }

    private static boolean looksLikeHexHash(String s) {
        if (s.length() != 32) return false;
        for (int i = 0; i < 32; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }
}
