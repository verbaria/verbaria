/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.action;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

import org.zanata.cdi.DeltaSpike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.ServletContext;

@ApplicationScoped
@Named("frontendAssets")
public class FrontendAssets implements Serializable {

    public static final String MANIFEST_PATH =
            "META-INF/resources/manifest.json";

    private static final long serialVersionUID = 1L;
    private static final Logger log =
            LoggerFactory.getLogger(FrontendAssets.class);

    private ServletContext servletContext;
    private FrontendManifest manifest;

    @Inject
    public FrontendAssets(@DeltaSpike ServletContext servletContext) {
        this.servletContext = servletContext;
        this.manifest = readManifest();
    }

    @SuppressWarnings("unused")
    protected FrontendAssets() {
    }

    public String getFrontendJs() {
        return servletContext.getContextPath() + "/" + manifest.frontendJs();
    }

    public String getFrontendCss() {
        return servletContext.getContextPath() + "/" + manifest.frontendCss();
    }

    public String getLegacyJs() {
        return servletContext.getContextPath() + "/" + manifest.legacyJs();
    }

    /**
     * runtime.js is the webpack runtime. It has to be loaded before any other javascript modules.
     */
    public String getRuntime() {
        return servletContext.getContextPath() + "/" + manifest.runtime();
    }

    public String getEditorJs() {
        return servletContext.getContextPath() + "/" + manifest.editorJs();
    }

    public String getEditorCss() {
        return servletContext.getContextPath() + "/" + manifest.editorCss();
    }

    public void init(@Observes @Initialized(jakarta.enterprise.context.ApplicationScoped.class) ServletContext context) {
        // just to make sure our manifest file can be read
        log.info("zanata frontend manifest: {}", manifest);
    }

    private static FrontendManifest readManifest() {
        InputStream manifestResource = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream(MANIFEST_PATH);
        if (manifestResource == null) {
            throw new IllegalStateException(
                    "Cannot load manifest.json from " + MANIFEST_PATH + ". "
                            + "Did you forget to build and include zanata-frontend?");
        }
        try (InputStream in = manifestResource) {
            return new ObjectMapper().readValue(in, FrontendManifest.class);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Cannot load manifest.json from " + MANIFEST_PATH, e);
        }
    }
}
