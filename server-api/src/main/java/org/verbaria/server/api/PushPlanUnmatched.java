package org.verbaria.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushPlanUnmatched(String path, String docId, String reason) {
}
