/*
 * Copyright 2018, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.util;

import java.net.URL;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;

/**
 * If you need to update dependencies.properties without running the whole build, try this:<br>
 * {@code mvn org.codehaus.gmavenplus:gmavenplus-plugin:execute@dependency-versions -pl :services}
 *
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
@Named("webjars")
@ApplicationScoped
public class WebJars {

    // These are the Maven groupIds for the three types of webjars
    private static final String BOWER = "org.webjars.bower";
    private static final String CLASSIC = "org.webjars";
    private static final String NPM = "org.webjars.npm";

    // these strings are all used in xhtml pages with h:outputScript, either in zanata-war or gwt-editor
    private volatile String commonmarkJS;
    private volatile String googleCajaHtmlSanitizerJS;
    private volatile String blueimpJavaScriptTemplatesJS;
    private volatile String crossroadsJS;
    private volatile String signalsJS;
    private volatile String diffJS;
    private volatile String jQueryTypingJS;

    public String getCommonmarkJS() {
        String value = commonmarkJS;
        if (value == null) {
            value = scriptName(BOWER, "commonmark", "dist/commonmark.min.js");
            commonmarkJS = value;
        }
        return value;
    }

    public String getGoogleCajaHtmlSanitizerJS() {
        String value = googleCajaHtmlSanitizerJS;
        if (value == null) {
            value = scriptName(BOWER, "google-caja", "html-sanitizer-minified.js");
            googleCajaHtmlSanitizerJS = value;
        }
        return value;
    }

    public String getBlueimpJavaScriptTemplatesJS() {
        String value = blueimpJavaScriptTemplatesJS;
        if (value == null) {
            value = scriptName(BOWER, "blueimp-tmpl", "js/tmpl.min.js");
            blueimpJavaScriptTemplatesJS = value;
        }
        return value;
    }

    public String getCrossroadsJS() {
        String value = crossroadsJS;
        if (value == null) {
            value = scriptName(CLASSIC, "crossroads.js", "crossroads.min.js");
            crossroadsJS = value;
        }
        return value;
    }

    public String getSignalsJS() {
        String value = signalsJS;
        if (value == null) {
            value = scriptName(CLASSIC, "js-signals", "signals.min.js");
            signalsJS = value;
        }
        return value;
    }

    public String getDiffJS() {
        String value = diffJS;
        if (value == null) {
            value = scriptName(NPM, "diff", "dist/diff.min.js");
            diffJS = value;
        }
        return value;
    }

    // Note that the JavaBean property name (for JSF) is JQueryTypingJS, not jQueryTypingJS
    // Ref: http://futuretask.blogspot.com/2005/01/java-tip-6-dont-capitalize-first-two.html
    public String getJQueryTypingJS() {
        String value = jQueryTypingJS;
        if (value == null) {
            // jquery-typing uses the version number in the paths inside the
            // package, so we have to handle it specially.
            String jqTypingLib = "github-com-ccakes-jquery-typing";
            String jqTypingVer = getJarVersion(BOWER, jqTypingLib);
            value = scriptName(BOWER, jqTypingLib,
                    "plugin/jquery.typing-" + jqTypingVer + ".min.js");
            jQueryTypingJS = value;
        }
        return value;
    }

    /**
     * Returns the URL for the specified webjar resource (whose name has been
     * returned by one of the other WebJars methods/properties). If the
     * resource is not found, an exception is thrown.
     */
    public URL getResource(String nameInsideJar) {
        String name = "/META-INF/resources/webjars/" + nameInsideJar;
        URL url = getClass().getResource(name);
        if (url == null) {
            throw new RuntimeException("resource not found: " + name);
        }
        return url;
    }

    /*
     * Gets the Maven version of a webjar artifact.
     * Throws IllegalStateException if the info is not available.
     */
    private static String getJarVersion(String groupId, String libName) {
        try {
            return Dependencies.getVersion(groupId + ":" + libName + ":jar");
        } catch (IllegalStateException e) {
            throw new IllegalStateException(
                    "no entry for " + groupId + ":" + libName + ":jar. "
                            + "Check that dependencies.properties is up to date",
                    e);
        }
    }

    /*
     * Returns the name of the implementation script for JSF h:outputScript.
     */
    private static String scriptName(String groupId, String libName,
            String resourceName) {
        String ver = getJarVersion(groupId, libName);
        return libName + "/" + ver + "/" + resourceName;
    }
}
