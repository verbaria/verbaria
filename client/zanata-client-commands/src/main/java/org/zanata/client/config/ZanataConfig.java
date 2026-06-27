/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.client.config;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * Representation of the root node of a project configuration
 *
 * @author Sean Flanigan <sflaniga@redhat.com>
 *
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ZanataConfig implements Serializable {
    private static final long serialVersionUID = 1L;
    private LocaleList locales = new LocaleList();
    private String project;
    private URL url;
    private String projectType;
    private String projectVersion;
    // default to current directory
    private String srcDir = ".";
    private String transDir = ".";
    private String includes;
    private String excludes;
    /**
     * Target locale IDs (e.g. {@code ["ru-RU", "zh-CN"]}). When set, the client
     * pulls/pushes only these locales and does NOT query the server for the
     * project's locale list. A simpler, JSON-friendly alternative to the
     * {@code locales} mapping list for the common "I know my target langs" case.
     */
    private List<String> targetLocales;
    private List<CommandHook> hooks = new ArrayList<CommandHook>();
    private transient Splitter splitter = Splitter.on(",").omitEmptyStrings()
            .trimResults();

    public ZanataConfig() {
    }

    public LocaleList getLocales() {
        return locales;
    }

    public void setLocales(LocaleList locales) {
        this.locales = locales;
    }

    public List<CommandHook> getHooks() {
        return hooks;
    }

    public void setHooks(List<CommandHook> commandHooks) {
        this.hooks = commandHooks;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public String getProjectType() {
        return projectType;
    }

    public void setProjectType(String type) {
        this.projectType = type;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String version) {
        this.projectVersion = version;
    }

    public String getSrcDir() {
        return srcDir;
    }

    public void setSrcDir(String srcDir) {
        this.srcDir = srcDir;
    }

    public String getTransDir() {
        return transDir;
    }

    public void setTransDir(String transDir) {
        this.transDir = transDir;
    }

    public String getIncludes() {
        return includes;
    }

    public void setIncludes(String includes) {
        this.includes = includes;
    }

    public String getExcludes() {
        return excludes;
    }

    public void setExcludes(String excludes) {
        this.excludes = excludes;
    }

    public List<String> getTargetLocales() {
        return targetLocales;
    }

    public void setTargetLocales(List<String> targetLocales) {
        this.targetLocales = targetLocales;
    }

    /**
     * Convert {@link #targetLocales} into a {@link LocaleList}, or return
     * {@code null} when none are configured (so callers fall back to the
     * {@code locales} mapping list / server fetch). Blank entries are ignored.
     */
    @JsonIgnore
    public LocaleList getTargetLocalesAsList() {
        if (targetLocales == null || targetLocales.isEmpty()) {
            return null;
        }
        LocaleList list = new LocaleList();
        for (String id : targetLocales) {
            if (id != null && !id.trim().isEmpty()) {
                list.add(new LocaleMapping(id.trim()));
            }
        }
        return list.isEmpty() ? null : list;
    }

    @JsonIgnore
    public File getSrcDirAsFile() {
        return new File(srcDir);
    }

    @JsonIgnore
    public File getTransDirAsFile() {
        return new File(transDir);
    }

    @JsonIgnore
    public Path getSrcDirAsPath() {
        return srcDir == null ? null : Paths.get(srcDir);
    }

    @JsonIgnore
    public Path getTransDirAsPath() {
        return transDir == null ? null : Paths.get(transDir);
    }

    @JsonIgnore
    public ImmutableList<String> getIncludesAsList() {
        if (includes != null) {
            return ImmutableList.copyOf(splitter.split(includes));
        }
        return ImmutableList.of();
    }

    @JsonIgnore
    public ImmutableList<String> getExcludesAsList() {
        if (excludes != null) {
            return ImmutableList.copyOf(splitter.split(excludes));
        }
        return ImmutableList.of();
    }

}
