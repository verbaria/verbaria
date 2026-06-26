package org.zanata.adapter.layout;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.zanata.common.ProjectType;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

public interface DocumentLayout {

    Set<ProjectType> supportedTypes();

    String fileExtension();

    String contentType();

    Optional<PathDoc> classify(String relativePath);

    String outputPath(String docId, String localeId);

    default String sourceOutputPath(String docId, String sourceLocaleId) {
        return outputPath(docId, sourceLocaleId);
    }

    Resource readSource(String docId, byte[] content) throws IOException;

    TranslationsResource readTranslation(byte[] content) throws IOException;

    byte[] writeSource(Resource source) throws IOException;

    byte[] writeTranslation(Resource source, TranslationsResource translation,
            String localeId) throws IOException;
}
