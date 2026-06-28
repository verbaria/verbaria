package org.verbaria.server.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushStartResponse(String sessionId) {
}
