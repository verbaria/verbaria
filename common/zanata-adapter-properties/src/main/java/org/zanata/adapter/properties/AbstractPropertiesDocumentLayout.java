package org.zanata.adapter.properties;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;

import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.dto.TranslatedDoc;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

public abstract class AbstractPropertiesDocumentLayout implements DocumentLayout {

    private static final String EXT = ".properties";
    private static final Pattern LOCALE_SUFFIX =
            Pattern.compile("_([a-z]{2,3}([_-][A-Za-z0-9]{1,8})*)$");

    private final PropWriter.CHARSET charset;

    protected AbstractPropertiesDocumentLayout(PropWriter.CHARSET charset) {
        this.charset = charset;
    }

    @Override
    public String fileExtension() {
        return EXT;
    }

    @Override
    public String contentType() {
        return "text/plain;charset=UTF-8";
    }

    @Override
    public Optional<PathDoc> classify(String relativePath) {
        String norm = relativePath.replace('\\', '/');
        if (!norm.endsWith(EXT)) {
            return Optional.empty();
        }
        String base = norm.substring(0, norm.length() - EXT.length());
        var matcher = LOCALE_SUFFIX.matcher(base);
        if (matcher.find()) {
            String localeId = matcher.group(1).replace('_', '-');
            String docId = base.substring(0, matcher.start());
            return Optional.of(new PathDoc(docId, localeId));
        }
        return Optional.of(new PathDoc(base, null));
    }

    @Override
    public String outputPath(String docId, String localeId) {
        if (localeId == null || localeId.isEmpty()) {
            return docId + EXT;
        }
        return docId + "_" + localeId.replace('-', '_') + EXT;
    }

    @Override
    public String sourceOutputPath(String docId, String sourceLocaleId) {
        return docId + EXT;
    }

    @Override
    public Resource readSource(String docId, byte[] content) throws IOException {
        Resource doc = new Resource(docId);
        doc.setLang(LocaleId.EN_US);
        new PropReader(charset, LocaleId.EN_US, ContentState.New)
                .extractTemplate(doc, new ByteArrayInputStream(content));
        return doc;
    }

    @Override
    public TranslationsResource readTranslation(byte[] content) throws IOException {
        TranslationsResource tr = new TranslationsResource();
        new PropReader(charset, LocaleId.EN_US, ContentState.Translated)
                .extractTarget(tr, new ByteArrayInputStream(content));
        return tr;
    }

    @Override
    public byte[] writeSource(Resource source) throws IOException {
        Path dir = Files.createTempDirectory("zanata-prop-src");
        try {
            PropWriter.writeSource(source, dir, charset);
            return Files.readAllBytes(dir.resolve(source.getName() + EXT));
        } finally {
            deleteRecursively(dir);
        }
    }

    @Override
    public byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException {
        File tmp = File.createTempFile("zanata-prop-trans", EXT);
        try {
            TranslatedDoc td =
                    new TranslatedDoc(source, translation, new LocaleId(localeId));

            PropWriter.writeTranslationsFile(td, tmp.toPath(), charset, false,
                    false);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }
}
