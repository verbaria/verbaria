package org.zanata.client.commands.push;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import org.zanata.client.commands.push.PushCommand.TranslationResourcesVisitor;
import org.zanata.client.config.LocaleMapping;
import org.zanata.common.LocaleId;
import org.zanata.adapter.chrome.ChromeReader;
import org.zanata.rest.StringSet;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

public class ChromeStrategy extends AbstractPushStrategy {

    private static final String LOCALES = "_locales/";
    private static final String FILE = "messages.json";

    public ChromeStrategy() {
        super(new StringSet("comment;gettext;chrome"), ".json");
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
        for (String rel : getSrcFiles(getOpts().getSrcDir(), includes, excludes,
                excludeLocaleFilenames, useDefaultExclude, caseSensitive)) {
            String locale = localeOf(rel);
            if (locale == null || !isSourceLocale(locale)) {
                continue;
            }
            docNames.add(docNameOf(rel));
        }
        return docNames;
    }

    @Override
    public Resource loadSrcDoc(Path sourceDir, String docName)
            throws IOException, RuntimeException {
        Path file = localeFile(sourceDir, docName, getOpts().getSourceLang());
        if (file == null) {
            throw new IOException("No source messages.json for docId: " + docName);
        }
        LocaleId src = new LocaleId(getOpts().getSourceLang());
        try (InputStream in = Files.newInputStream(file)) {
            Resource doc = new ChromeReader().extractTemplate(docName, in);
            doc.setLang(src);
            return doc;
        }
    }

    @Override
    public void visitTranslationResources(String docName, Resource srcDoc,
            TranslationResourcesVisitor callback)
            throws IOException, RuntimeException {
        if (getOpts().getLocaleMapList() == null) {
            return;
        }
        for (LocaleMapping locale : getOpts().getLocaleMapList()) {
            Path file = localeFile(getOpts().getTransDir(), docName,
                    locale.getLocale());
            if (file == null) {
                continue;
            }
            try (InputStream in = Files.newInputStream(file)) {
                TranslationsResource tr = new ChromeReader().extractTarget(in);
                if (!tr.getTextFlowTargets().isEmpty()) {
                    callback.visit(locale, tr);
                }
            }
        }
    }

    private boolean isSourceLocale(String localeDir) {
        return localeMatches(localeDir, getOpts().getSourceLang());
    }

    /** Locale segment of a {@code .../_locales/<locale>/messages.json} path. */
    private static String localeOf(String rel) {
        String norm = rel.replace('\\', '/');
        if (!norm.endsWith(FILE)) {
            return null;
        }
        int idx = norm.indexOf(LOCALES);
        if (idx < 0) {
            return null;
        }
        String tail = norm.substring(idx + LOCALES.length());
        int slash = tail.indexOf('/');
        return slash < 0 ? null : tail.substring(0, slash);
    }

    /**
     * Locale-independent document id: the path prefix before {@code _locales/}.
     * {@code src/main/chrome/_locales/en/messages.json} &rarr;
     * {@code src/main/chrome/messages}.
     */
    private static String docNameOf(String rel) {
        String norm = rel.replace('\\', '/');
        int idx = norm.indexOf(LOCALES);
        String prefix = idx <= 0 ? "" : norm.substring(0, idx - 1);
        return prefix.isEmpty() ? "messages" : prefix + "/messages";
    }

    /** Resolve {@code <base>/<prefix>/_locales/<localeDir>/messages.json}. */
    private static Path localeFile(Path base, String docName, String locale)
            throws IOException {
        String prefix = docName.equals("messages") ? ""
                : docName.substring(0, docName.length() - "/messages".length());
        Path locales = (prefix.isEmpty() ? base : base.resolve(prefix))
                .resolve("_locales");
        if (!Files.isDirectory(locales)) {
            return null;
        }
        Path dir = resolveLocaleDir(locales, locale);
        if (dir == null) {
            return null;
        }
        Path file = dir.resolve(FILE);
        return Files.isRegularFile(file) ? file : null;
    }

    private static Path resolveLocaleDir(Path locales, String locale)
            throws IOException {
        try (var children = Files.newDirectoryStream(locales, Files::isDirectory)) {
            for (Path child : children) {
                if (localeMatches(child.getFileName().toString(), locale)) {
                    return child;
                }
            }
        }
        return null;
    }

    /**
     * Match a {@code _locales} directory name against a server locale id,
     * tolerating separator ({@code en-US}/{@code en_US}) and language-only
     * ({@code en}) forms, case-insensitively.
     */
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
