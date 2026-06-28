package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushPlanEntry(String path, String docId, String localeId,
        String project, boolean source) {
}
