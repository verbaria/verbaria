package org.zanata.client.commands.pull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.LineBreak;
import org.yaml.snakeyaml.Yaml;
import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.common.io.FileDetails;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;

public class YamlStrategy extends AbstractPullStrategy {

    private static final String RAW_CONTENT_KEY = "content";
    private static final String[] EXTS = {".yaml", ".yml", ".html", ".properties", ".java", ".colorPage"};
    private final StringSet extensions = new StringSet("comment;gettext");

    protected YamlStrategy(PullOptions opts) {
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

    @Override
    public void writeSrcFile(Resource doc) throws IOException {
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
        File transDir = getOpts().getTransDir();
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
