/*
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.commands.glossary.pull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.zanata.client.commands.ConfigurableCommand;
import org.zanata.client.commands.OptionsUtil;
import org.zanata.rest.client.GlossaryClient;
import org.zanata.rest.client.RestClientFactory;
import org.zanata.util.PathUtil;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

/**
 *
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 *
 **/
public class GlossaryPullCommand extends
        ConfigurableCommand<GlossaryPullOptions> {
    private static final Logger log =
            LoggerFactory.getLogger(GlossaryPullCommand.class);

    private final GlossaryClient client;

    public GlossaryPullCommand(GlossaryPullOptions opts,
            RestClientFactory clientFactory) {
        super(opts, clientFactory);
        client = getClientFactory().getGlossaryClient();
    }

    public GlossaryPullCommand(GlossaryPullOptions opts) {
        this(opts, OptionsUtil.createClientFactory(opts));
    }

    @Override
    public void run() throws Exception {
        String fileType = StringUtils.isEmpty(getOpts().getFileType()) ? "csv"
                : getOpts().getFileType();
        if (!fileType.equalsIgnoreCase("po")
                && !fileType.equalsIgnoreCase("csv")) {
            throw new RuntimeException(
                    "Option '--file-type' is not valid. Please use 'csv' or 'po'");
        }

        log.info("Server: {}", getOpts().getUrl());
        log.info("Username: {}", getOpts().getUsername());
        log.info("File type: {}", fileType);
        if (StringUtils.isNotBlank(getOpts().getProject())) {
            log.info("Project: {}", getOpts().getProject());
        }
        ImmutableList<String> transLang = getOpts().getTransLang();
        if (transLang != null && !transLang.isEmpty()) {
            log.info("Translation language: {}", Joiner.on(",").join(transLang));
        }

        String project = getOpts().getProject();
        String qualifiedName;
        try {
            qualifiedName = StringUtils.isBlank(project)
                    ? client.getGlobalQualifiedName()
                    : client.getProjectQualifiedName(project);
        } catch (HttpClientErrorException.NotFound nf) {
            log.error("Project {} not found", project);
            return;
        }

        log.info("Pulling glossary from server");
        ResponseEntity<byte[]> response;
        try {
            response =
                    client.downloadFile(fileType, transLang, qualifiedName);
        } catch (HttpClientErrorException.NotFound nf) {
            log.info("No glossary file in server");
            return;
        }

        byte[] glossaryFile = response.getBody();
        if (glossaryFile == null || glossaryFile.length == 0) {
            log.info("No glossary file in server");
            return;
        }

        String fileName = filenameFromHeader(response);
        if (fileName == null) {
            log.error("Null filename response from server: {}",
                    response.getStatusCode());
            return;
        }

        File file = new File(fileName);
        PathUtil.makeDirs(file.getParentFile());
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(glossaryFile);
            out.flush();
        }
        log.info("Glossary pulled to {}", fileName);
    }

    private static String filenameFromHeader(ResponseEntity<?> response) {
        HttpHeaders headers = response.getHeaders();
        List<String> values =
                headers.get(HttpHeaders.CONTENT_DISPOSITION);
        if (values == null || values.isEmpty()) {
            return null;
        }
        for (String header : values) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                if (trimmed.startsWith("filename=")) {
                    String name = trimmed.substring("filename=".length());
                    if (name.startsWith("\"") && name.endsWith("\"")
                            && name.length() >= 2) {
                        name = name.substring(1, name.length() - 1);
                    }
                    return name;
                }
            }
        }
        return null;
    }
}
