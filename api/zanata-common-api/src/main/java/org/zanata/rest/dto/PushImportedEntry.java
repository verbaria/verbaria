package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushImportedEntry(String path, String docId, String localeId,
        String project, boolean source) {
}
