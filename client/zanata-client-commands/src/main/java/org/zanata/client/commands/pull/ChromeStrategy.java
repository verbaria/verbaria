package org.zanata.client.commands.pull;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.zanata.adapter.chrome.ChromeWriter;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.common.io.FileDetails;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;

public class ChromeStrategy extends AbstractPullStrategy {

    private static final String FILE = "messages.json";
    private final StringSet extensions = new StringSet("comment;gettext;chrome");

    protected ChromeStrategy(PullOptions opts) {
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
    public Path getTransFileToWrite(String docName, LocaleMapping localeMapping) {
        return messagesFile(getOpts().getTransDir(), docName,
                localeDirName(getOpts().getTransDir(), docName,
                        localeMapping.getLocale()));
    }

    @Override
    public void writeSrcFile(Resource doc) throws IOException {
        String srcLocale = doc.getLang() == null ? "en-US"
                : doc.getLang().getId();
        Map<String, String> srcByResId = new LinkedHashMap<>();
        for (TextFlow tf : doc.getTextFlows()) {
            if (tf.getContents() != null && !tf.getContents().isEmpty()) {
                srcByResId.put(tf.getId(), tf.getContents().get(0));
            }
        }
        Map<String, ChromeWriter.Entry> entries = entries(doc, srcByResId);
        if (entries.isEmpty()) {
            return;
        }
        Path out = messagesFile(getOpts().getSrcDir(), doc.getName(),
                localeDirName(getOpts().getSrcDir(), doc.getName(), srcLocale));
        write(out, entries);
    }

    @Override
    public FileDetails writeTransFile(String docName,
            LocaleMappedTranslatedDoc doc) throws IOException {
        if (doc.getSource() == null) {
            return null;
        }
        Map<String, String> transByResId = new LinkedHashMap<>();
        if (doc.getTranslation() != null
                && doc.getTranslation().getTextFlowTargets() != null) {
            for (TextFlowTarget t : doc.getTranslation().getTextFlowTargets()) {
                if (t.getContents() == null || t.getContents().isEmpty()) {
                    continue;
                }
                String c = t.getContents().get(0);
                if (c != null && !c.isEmpty()) {
                    transByResId.put(t.getResId(), c);
                }
            }
        }
        Map<String, ChromeWriter.Entry> entries =
                entries(doc.getSource(), transByResId);
        if (entries.isEmpty()) {
            return null;
        }
        Path out = getTransFileToWrite(docName, doc.getLocale());
        write(out, entries);
        return null;
    }

    private static Map<String, ChromeWriter.Entry> entries(Resource source,
            Map<String, String> contentByResId) {
        Map<String, ChromeWriter.Entry> entries = new LinkedHashMap<>();
        for (TextFlow tf : source.getTextFlows()) {
            String content = contentByResId.get(tf.getId());
            if (content == null) {
                continue;
            }
            ChromeMessage meta = tf.getExtensions() == null ? null
                    : tf.getExtensions().findByType(ChromeMessage.class);
            SimpleComment comment = tf.getExtensions() == null ? null
                    : tf.getExtensions().findByType(SimpleComment.class);
            String description = comment == null ? null : comment.getValue();
            entries.put(humanKey(tf),
                    new ChromeWriter.Entry(content, description, meta));
        }
        return entries;
    }

    private static void write(Path out, Map<String, ChromeWriter.Entry> entries)
            throws IOException {
        Files.createDirectories(out.getParent());
        try (Writer w = new OutputStreamWriter(Files.newOutputStream(out),
                StandardCharsets.UTF_8)) {
            new ChromeWriter().write(w, entries);
        }
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

    private static Path messagesFile(Path base, String docName,
            String localeDirName) {
        String prefix = docName.equals("messages") ? ""
                : docName.substring(0, docName.length() - "/messages".length());
        Path locales = (prefix.isEmpty() ? base : base.resolve(prefix))
                .resolve("_locales");
        return locales.resolve(localeDirName).resolve(FILE);
    }

    private static String localeDirName(Path base, String docName,
            String locale) {
        String prefix = docName.equals("messages") ? ""
                : docName.substring(0, docName.length() - "/messages".length());
        Path locales = (prefix.isEmpty() ? base : base.resolve(prefix))
                .resolve("_locales");
        if (Files.isDirectory(locales)) {
            try (var children =
                    Files.newDirectoryStream(locales, Files::isDirectory)) {
                for (Path child : children) {
                    if (localeMatches(child.getFileName().toString(), locale)) {
                        return child.getFileName().toString();
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return locale.replace('-', '_');
    }

    private static boolean localeMatches(String dirName, String locale) {
        List<String> candidates = List.of(locale, locale.replace('-', '_'),
                locale.replace('_', '-'), langOnly(locale));
        for (String c : candidates) {
            if (dirName.equalsIgnoreCase(c)) {
                return true;
            }
        }
        return false;
    }

    private static String langOnly(String locale) {
        int sep = locale.indexOf('-');
        if (sep < 0) {
            sep = locale.indexOf('_');
        }
        return sep < 0 ? locale : locale.substring(0, sep);
    }
}
