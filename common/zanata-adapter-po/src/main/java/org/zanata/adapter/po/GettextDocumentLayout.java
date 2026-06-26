package org.zanata.adapter.po;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

@Component
public final class GettextDocumentLayout implements DocumentLayout {

    private static final String POT = ".pot";
    private static final String PO = ".po";

    @Override
    public Set<ProjectType> supportedTypes() {
        return Set.of(ProjectType.Gettext, ProjectType.Podir,
                ProjectType.Xml, ProjectType.File);
    }

    @Override
    public String fileExtension() {
        return PO;
    }

    @Override
    public String contentType() {
        return "text/plain;charset=UTF-8";
    }

    @Override
    public List<String> scanPatterns() {
        return List.of("**/*" + POT, "**/*" + PO);
    }

    @Override
    public Optional<PathDoc> classify(String relativePath) {
        String norm = relativePath.replace('\\', '/');
        if (norm.endsWith(POT)) {
            return Optional.of(new PathDoc(
                    norm.substring(0, norm.length() - POT.length()), null));
        }
        if (!norm.endsWith(PO)) {
            return Optional.empty();
        }
        String body = norm.substring(0, norm.length() - PO.length());
        int lastSlash = body.lastIndexOf('/');
        if (lastSlash < 0) {
            return Optional.of(new PathDoc(body, null));
        }
        String stem = body.substring(lastSlash + 1);
        String dir = body.substring(0, lastSlash);
        int prevSlash = dir.lastIndexOf('/');
        String localeId = dir.substring(prevSlash + 1);
        String prefix = prevSlash < 0 ? "" : dir.substring(0, prevSlash);
        String docId = prefix.isEmpty() ? stem : prefix + "/" + stem;
        return Optional.of(new PathDoc(docId, localeId));
    }

    @Override
    public String sourceOutputPath(String docId, String sourceLocaleId) {
        return docId + POT;
    }

    @Override
    public String outputPath(String docId, String localeId) {
        if (localeId == null || localeId.isEmpty()) {
            return docId + POT;
        }
        int lastSlash = docId.lastIndexOf('/');
        if (lastSlash < 0) {
            return localeId + "/" + docId + PO;
        }
        return docId.substring(0, lastSlash) + "/" + localeId + "/"
                + docId.substring(lastSlash + 1) + PO;
    }

    @Override
    public Resource readSource(String docId, byte[] content) {
        return new PoReader2().extractTemplate(
                new InputSource(new ByteArrayInputStream(content)),
                LocaleId.EN_US, docId);
    }

    @Override
    public TranslationsResource readTranslation(byte[] content) {
        return new PoReader2()
                .extractTarget(new InputSource(new ByteArrayInputStream(content)));
    }

    @Override
    public byte[] writeSource(Resource source) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PoWriter2().writePot(out, "UTF-8", source);
        return out.toByteArray();
    }

    @Override
    public byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new PoWriter2().writePo(out, "UTF-8", source, translation);
        return out.toByteArray();
    }
}
