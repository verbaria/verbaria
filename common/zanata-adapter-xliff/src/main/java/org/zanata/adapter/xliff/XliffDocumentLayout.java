package org.zanata.adapter.xliff;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

@Component
public final class XliffDocumentLayout implements DocumentLayout {

    private static final String EXT = ".xml";
    private static final Pattern LOCALE_SUFFIX =
            Pattern.compile("_([a-z]{2,3}([_-][A-Za-z0-9]{1,8})*)$");

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Xliff);
    }

    @Override
    public String fileExtension() {
        return ".xlf";
    }

    @Override
    public String contentType() {
        return "application/xliff+xml";
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
            return Optional.of(new PathDoc(base.substring(0, matcher.start()),
                    matcher.group(1).replace('_', '-')));
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
        Path tmp = Files.createTempFile("zanata-xliff-src", EXT);
        try {
            Files.write(tmp, content);
            return new XliffReader()
                    .extractTemplate(tmp, LocaleId.EN_US, docId, "CONTENT");
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public TranslationsResource readTranslation(byte[] content) throws IOException {
        Path tmp = Files.createTempFile("zanata-xliff-trans", EXT);
        try {
            Files.write(tmp, content);
            return new XliffReader().extractTarget(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    @Override
    public byte[] writeSource(Resource source) throws IOException {
        return write(source, source.getLang() == null ? "en-US"
                : source.getLang().getId(), null);
    }

    @Override
    public byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException {
        return write(source, localeId, translation);
    }

    private static byte[] write(Resource source, String localeId,
            TranslationsResource translation) throws IOException {
        File tmp = File.createTempFile("zanata-xliff-out", EXT);
        try {
            XliffWriter.writeFile(tmp.toPath(), source, localeId, translation,
                    true, false);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }
}
