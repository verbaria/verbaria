package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushPlanUnmatched(String path, String docId, String reason) {
}
