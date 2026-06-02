package org.zanata.client.commands.pull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.commons.io.FilenameUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.common.io.FileDetails;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import java.nio.file.Paths;

public class ConsuloStrategy extends AbstractPullStrategy {

    private static final Logger log = LoggerFactory.getLogger(ConsuloStrategy.class);

    private static final String RAW_CONTENT_KEY = "content";
    private static final String[] EXTS = {".yaml", ".yml", ".html", ".properties", ".java", ".colorPage"};
    private final StringSet extensions = new StringSet("comment;gettext;consulo");
    /** Lazily-built index of existing on-disk source files, keyed by docName. */
    private SourceIndex sourceIndex;

    protected ConsuloStrategy(PullOptions opts) {
        super(opts);
    }

    @Override
    public StringSet getExtensions() {
        return extensions;
    }

    @Override
    public boolean needsDocToWriteTrans() {
        return true;
    }

    /**
     * Pull the (possibly edited) source back into the on-disk en_US source.
     * <p>
     * A raw sub-file carries a {@link ConsuloSubFile} extension holding only its
     * file extension; its sub-path is recovered from the key ({@code .} →
     * {@code /}). We locate the doc's {@code LOCALIZE-LIB/<locale>/<doc>/} dir
     * and (re)create the exact file there — no extension is ever hardcoded or
     * guessed, and files not yet on disk are created too. If the dir can't be
     * located we report an error rather than scatter files. An entry with no
     * {@link ConsuloSubFile} is a plain {@code key: {text}} yaml value.
     * <p>
     * For older server data that predates the extension we fall back to
     * matching the key against the existing on-disk shape.
     */
    @Override
    public void writeSrcFile(Resource doc) throws IOException {
        String docName = doc.getName();
        Map<String, String> yamlEntries = new LinkedHashMap<>();
        // recovered rel sub-path (with extension) -> raw body.
        Map<String, String> rawBySubPath = new LinkedHashMap<>();
        // key -> raw body, for the disk-matching fallback (legacy data).
        Map<String, String> rawByKey = new LinkedHashMap<>();
        for (TextFlow tf : doc.getTextFlows()) {
            String key = humanKey(tf);
            if (key == null) continue;
            List<String> contents = tf.getContents();
            if (contents == null || contents.isEmpty()) continue;
            String value = contents.get(0);
            if (value == null) continue;
            String ext = consuloExt(tf);
            if (ext != null) {
                rawBySubPath.put(subPath(key, ext), value);
            } else {
                yamlEntries.put(key, value);
                rawByKey.put(key, value);
            }
        }
        if (yamlEntries.isEmpty() && rawBySubPath.isEmpty()) return;

        String srcLocale = doc.getLang() == null ? "en-US"
                : doc.getLang().getId();
        String localeDir = srcLocale.replace('-', '_');
        SourceIndex index = sourceIndex(localeDir);
        String docKey = docName.toLowerCase(Locale.ROOT);

        // 1) Raw sub-files: rebuild the path from the key + extension and
        //    (re)create the file under the doc dir (even if absent).
        if (!rawBySubPath.isEmpty()) {
            Path docDir = resolveDocDir(index, docName, localeDir);
            if (docDir == null) {
                log.error("Cannot add {} consulo source file(s) for {}: no "
                        + "LOCALIZE-LIB/{}/ directory found in {}",
                        rawBySubPath.size(), docName, localeDir,
                        getOpts().getSrcDir());
                return;
            }
            for (Map.Entry<String, String> e : rawBySubPath.entrySet()) {
                Path target = docDir.resolve(e.getKey());
                Path parent = target.getParent();
                if (parent != null) Files.createDirectories(parent);
                Files.writeString(target, e.getValue(),
                        StandardCharsets.UTF_8);
                log.info("Wrote source file {}", target);
            }
            return;
        }

        // 2) No consulo extension → route by the existing on-disk shape.
        Path yaml = index.yamlByDoc.get(docKey);
        if (yaml != null) {
            writeYamlSource(yaml, yamlEntries);
            return;
        }
        Map<String, Path> subFiles = index.subFilesByDoc.get(docKey);
        if (subFiles != null) {
            writeRawSubFilesByKey(docName, rawByKey, subFiles);
            return;
        }
        Path raw = index.rawByDoc.get(docKey);
        if (raw != null) {
            // A single raw file (e.g. .html/.properties): the doc is one body.
            String body = rawByKey.containsKey(RAW_CONTENT_KEY)
                    ? rawByKey.get(RAW_CONTENT_KEY)
                    : rawByKey.values().iterator().next();
            Files.writeString(raw, body, StandardCharsets.UTF_8);
            log.info("Wrote source file {}", raw);
            return;
        }
        log.warn("No existing source file for {} under LOCALIZE-LIB/{}; "
                + "skipping source write", docName, localeDir);
    }

    /** Rel sub-path of a raw file: key {@code .}→{@code /} plus its extension. */
    private static String subPath(String key, String ext) {
        String path = key.replace('.', '/');
        return ext.isEmpty() ? path : path + "." + ext;
    }

    /**
     * The consulo file extension of a text flow, or {@code null} when the flow
     * is not a raw consulo file. The value may be an empty string (a raw file
     * that genuinely has no extension); only a missing extension yields null.
     */
    private static String consuloExt(TextFlow tf) {
        if (tf.getExtensions() == null) return null;
        ConsuloSubFile cf = tf.getExtensions().findByType(ConsuloSubFile.class);
        if (cf == null) return null;
        return cf.getExtension() == null ? "" : cf.getExtension();
    }

    /**
     * Locate the {@code <doc>/} directory to write raw sub-files into: the one
     * already indexed if it exists, otherwise (unambiguously) a fresh dir under
     * the single {@code LOCALIZE-LIB/<localeDir>/} root in the tree.
     */
    private Path resolveDocDir(SourceIndex index, String docName,
            String localeDir) {
        Path existing = index.docDirByDoc.get(docName.toLowerCase(Locale.ROOT));
        if (existing != null) return existing;
        if (index.localeRoots.size() == 1) {
            return index.localeRoots.iterator().next().resolve(docName);
        }
        return null;
    }

    /** Rewrite a top-level yaml source as sorted {@code key: {text: value}}. */
    private void writeYamlSource(Path out, Map<String, String> entries)
            throws IOException {
        // Sorted by key so the output order matches the (alphabetical) source.
        Map<String, Map<String, String>> rendered = new TreeMap<>();
        for (Map.Entry<String, String> e : entries.entrySet()) {
            Map<String, String> body = new LinkedHashMap<>(1);
            body.put("text", e.getValue());
            rendered.put(e.getKey(), body);
        }
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(FlowStyle.BLOCK);
        opts.setLineBreak(LineBreak.UNIX);
        opts.setIndent(4);
        opts.setSplitLines(false);
        try (OutputStreamWriter w = new OutputStreamWriter(
                Files.newOutputStream(out), StandardCharsets.UTF_8)) {
            new Yaml(opts).dump(rendered, w);
        }
        log.info("Wrote source file {}", out);
    }

    /**
     * Fallback for reference-less data: overwrite each raw sub-file with its
     * body, matching the key against the existing files on disk (so the real
     * extension comes from disk). Entries without a match are skipped.
     */
    private void writeRawSubFilesByKey(String docName,
            Map<String, String> entries, Map<String, Path> subFiles)
            throws IOException {
        for (Map.Entry<String, String> e : entries.entrySet()) {
            Path target = subFiles.get(e.getKey().toLowerCase(Locale.ROOT));
            if (target == null) {
                log.warn("No existing sub-file for {} / {}; skipping",
                        docName, e.getKey());
                continue;
            }
            Files.writeString(target, e.getValue(),
                    StandardCharsets.UTF_8);
            log.info("Wrote source file {}", target);
        }
    }

    /**
     * Index existing on-disk source under any {@code LOCALIZE-LIB/<localeDir>/}
     * (build outputs under {@code target/} ignored). Each doc lives directly
     * under that dir as either a {@code <doc>.<ext>} file or a {@code <doc>/}
     * directory of raw sub-files. Walked once and cached.
     */
    private SourceIndex sourceIndex(String localeDir) throws IOException {
        if (sourceIndex == null) {
            SourceIndex idx = new SourceIndex();
            Path root = getOpts().getSrcDir();
            if (root == null) root = Paths.get(".");
            java.nio.file.FileSystem fs = root.getFileSystem();
            String needle = ("/LOCALIZE-LIB/" + localeDir + "/")
                    .toLowerCase(Locale.ROOT);
            try (Stream<Path> stream = Files.walk(root)) {
                stream.filter(Files::isRegularFile).forEach(path -> {
                    String p = path.toString().replace('\\', '/');
                    String lower = p.toLowerCase(Locale.ROOT);
                    if (lower.contains("/target/")) return;
                    int at = lower.indexOf(needle);
                    if (at < 0) return;
                    // Original-case LOCALIZE-LIB/<localeDir>/ root path.
                    String localeRootStr = p.substring(0, at + needle.length());
                    idx.localeRoots.add(fs.getPath(localeRootStr));
                    String afterLocale = p.substring(at + needle.length());
                    int slash = afterLocale.indexOf('/');
                    if (slash < 0) {
                        // Top-level file: <doc>.<ext>
                        String name = afterLocale;
                        int dot = name.lastIndexOf('.');
                        if (dot < 0) return;
                        String doc = name.substring(0, dot)
                                .toLowerCase(Locale.ROOT);
                        String ext = name.substring(dot + 1)
                                .toLowerCase(Locale.ROOT);
                        if (ext.equals("yaml") || ext.equals("yml")) {
                            idx.yamlByDoc.putIfAbsent(doc, path);
                        } else {
                            idx.rawByDoc.putIfAbsent(doc, path);
                        }
                    } else {
                        // Inside a directory doc: <doc>/<sub-path>
                        String docSeg = afterLocale.substring(0, slash);
                        String doc = docSeg.toLowerCase(Locale.ROOT);
                        idx.docDirByDoc.putIfAbsent(doc,
                                fs.getPath(localeRootStr + docSeg));
                        String relInside = afterLocale.substring(slash + 1);
                        String subKey = FilenameUtils
                                .removeExtension(relInside)
                                .replace('/', '.')
                                .toLowerCase(Locale.ROOT);
                        idx.subFilesByDoc
                                .computeIfAbsent(doc, k -> new HashMap<>())
                                .putIfAbsent(subKey, path);
                    }
                });
            }
            sourceIndex = idx;
        }
        return sourceIndex;
    }

    /** Existing on-disk source layout, keyed by lowercase docName. */
    private static final class SourceIndex {
        final Map<String, Path> yamlByDoc = new HashMap<>();
        final Map<String, Path> rawByDoc = new HashMap<>();
        final Map<String, Map<String, Path>> subFilesByDoc = new HashMap<>();
        final Map<String, Path> docDirByDoc = new HashMap<>();
        final java.util.Set<Path> localeRoots = new java.util.HashSet<>();
    }

    @Override
    public FileDetails writeTransFile(String docName,
            LocaleMappedTranslatedDoc doc) throws IOException {
        Map<String, String> targetByResId = new LinkedHashMap<>();
        if (doc.getTranslation() != null
                && doc.getTranslation().getTextFlowTargets() != null) {
            for (TextFlowTarget t : doc.getTranslation().getTextFlowTargets()) {
                if (t.getContents() == null || t.getContents().isEmpty()) continue;
                String c = t.getContents().get(0);
                if (c == null || c.isEmpty()) continue;
                targetByResId.put(t.getResId(), c);
            }
        }

        boolean rawSingleEntry = doc.getSource() != null
                && doc.getSource().getTextFlows().size() == 1
                && RAW_CONTENT_KEY.equals(humanKey(doc.getSource().getTextFlows().get(0)));
        String locale = doc.getLocale().getLocale();
        File out = locateOutputFile(docName, locale, rawSingleEntry);
        File parent = out.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create dir: " + parent);
        }

        if (rawSingleEntry) {
            String resId = doc.getSource().getTextFlows().get(0).getId();
            String body = targetByResId.get(resId);
            if (body == null) return null;
            Files.writeString(out.toPath(), body, StandardCharsets.UTF_8);
            return null;
        }

        Map<String, Map<String, String>> rendered = new LinkedHashMap<>();
        if (doc.getSource() != null) {
            for (TextFlow tf : doc.getSource().getTextFlows()) {
                String translation = targetByResId.get(tf.getId());
                if (translation == null) continue;
                String key = humanKey(tf);
                if (key == null) continue;
                Map<String, String> body = new LinkedHashMap<>(1);
                body.put("text", translation);
                rendered.put(key, body);
            }
        }

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(FlowStyle.BLOCK);
        opts.setLineBreak(LineBreak.UNIX);
        opts.setIndent(4);
        opts.setSplitLines(false);
        try (OutputStreamWriter w = new OutputStreamWriter(
                new FileOutputStream(out), StandardCharsets.UTF_8)) {
            new Yaml(opts).dump(rendered, w);
        }
        return null;
    }

    /**
     * Pick the on-disk file path. If an existing file (any extension) matches
     * the docId case-insensitively, reuse its path so we don't create a
     * lowercase duplicate next to a mixed-case original. Otherwise default to
     * {@code <trans-dir>/<locale>/<docId>.yaml} (or .html for raw single-entry).
     */
    private File locateOutputFile(String docName, String locale, boolean raw) {
        File transDir = getOpts().getTransDir().toFile();
        File localeRoot = new File(transDir, locale);
        if (!localeRoot.exists()) {
            File alt = new File(transDir, "LOCALIZE-LIB/" + locale);
            if (alt.exists()) localeRoot = alt;
        }
        if (!localeRoot.exists() && !localeRoot.mkdirs()) {
            return new File(transDir, locale + "/" + docName + ".yaml");
        }
        String relFlat = docName.toLowerCase(Locale.ROOT);
        for (String ext : EXTS) {
            File existing = caseInsensitivePath(localeRoot, relFlat + ext);
            if (existing != null) return existing;
        }
        for (int i = docName.length() - 1; i >= 0; i--) {
            if (docName.charAt(i) != '.') continue;
            String dirs = docName.substring(0, i).replace('.', '/');
            String name = docName.substring(i + 1);
            for (String ext : EXTS) {
                File existing = caseInsensitivePath(localeRoot, dirs + "/" + name + ext);
                if (existing != null) return existing;
            }
        }
        String defaultExt = raw ? ".html" : ".yaml";
        return new File(localeRoot, docName + defaultExt);
    }

    private static File caseInsensitivePath(File root, String relPath) {
        File current = root;
        for (String want : relPath.split("/")) {
            File[] children = current.listFiles();
            if (children == null) return null;
            File match = null;
            for (File c : children) {
                if (c.getName().equalsIgnoreCase(want)) {
                    match = c;
                    break;
                }
            }
            if (match == null) return null;
            current = match;
        }
        return current.exists() ? current : null;
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
