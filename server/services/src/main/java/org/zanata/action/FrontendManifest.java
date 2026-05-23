/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 */
package org.zanata.action;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FrontendManifest(
        @JsonProperty("editor.css") String editorCss,
        @JsonProperty("editor.js") String editorJs,
        @JsonProperty("frontend.css") String frontendCss,
        @JsonProperty("frontend.js") String frontendJs,
        @JsonProperty("frontend.legacy.js") String legacyJs,
        @JsonProperty("intl-polyfill.js") String intlPolyFillJs,
        @JsonProperty("runtime.js") String runtime
) {
}
