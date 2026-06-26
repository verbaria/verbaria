package org.zanata.adapter.layout;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.zanata.common.ProjectType;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

public interface DocumentLayout {

    Set<ProjectType> supportedTypes();

    String fileExtension();

    String contentType();

    default List<String> scanPatterns() {
        return List.of("**/*" + fileExtension());
    }

    Optional<PathDoc> classify(String relativePath);

    String outputPath(String docId, String localeId);

    default String sourceOutputPath(String docId, String sourceLocaleId) {
        return outputPath(docId, sourceLocaleId);
    }

    Resource readSource(String docId, byte[] content) throws IOException;

    default Resource readSource(String docId, Map<String, byte[]> files)
            throws IOException {
        return readSource(docId, files.values().iterator().next());
    }

    TranslationsResource readTranslation(byte[] content) throws IOException;

    byte[] writeSource(Resource source) throws IOException;

    default Map<String, byte[]> writeSourceFiles(Resource source,
            String sourceLocaleId) throws IOException {
        return Map.of(sourceOutputPath(source.getName(), sourceLocaleId),
                writeSource(source));
    }

    byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException;

    default Map<String, byte[]> writeTranslationFiles(Resource source,
            TranslationsResource translation, String localeId)
            throws IOException {
        return Map.of(outputPath(source.getName(), localeId),
                writeTranslation(source, translation, localeId));
    }
}
