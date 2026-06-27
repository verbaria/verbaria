/*
 * Copyright 2026, Verbaria contributors.
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
 * along with this software; if not, see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.commands.changelog;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.zanata.client.commands.ZanataCommand;

public class ChangelogCommand implements ZanataCommand {
    private static final Logger log =
            LoggerFactory.getLogger(ChangelogCommand.class);

    private final ChangelogOptions opts;
    private final RestTemplate rest = new RestTemplate();

    public ChangelogCommand(ChangelogOptions opts) {
        this.opts = opts;
    }

    @Override
    public void runWithActions() throws Exception {
        File newFile = opts.getNewLock();
        if (newFile == null || !newFile.isFile()) {
            throw new RuntimeException("New lock file not found: " + newFile);
        }
        if (opts.getUrl() == null) {
            throw new RuntimeException("Server URL is required (--url)");
        }

        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("new", filePart(newFile, "new.json"));
        File oldFile = opts.getOldLock();
        if (oldFile != null && oldFile.isFile()) {
            parts.add("old", filePart(oldFile, "old.json"));
        }
        if (opts.getFormat() != null) {
            parts.add("format", opts.getFormat());
        }
        if (opts.getExcludeAuthors() != null) {
            for (String who : opts.getExcludeAuthors()) {
                parts.add("excludeAuthors", who);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Auth-User",
                opts.getUsername() == null ? "" : opts.getUsername());
        headers.set("X-Auth-Token",
                opts.getKey() == null ? "" : opts.getKey());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        String output = rest.postForObject(base() + "rest/changelog",
                new HttpEntity<>(parts, headers), String.class);
        if (output == null) {
            output = "";
        }

        File outFile = opts.getOutput();
        if (outFile != null) {
            Files.write(outFile.toPath(),
                    output.getBytes(StandardCharsets.UTF_8));
            log.info("Wrote changelog to {}", outFile);
        } else if (!output.isEmpty()) {
            PrintStream out = System.out;
            out.print(output);
            if (!output.endsWith("\n")) {
                out.println();
            }
            out.flush();
        }
    }

    private static ByteArrayResource filePart(File file, String name)
            throws Exception {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return name;
            }
        };
    }

    private String base() {
        String url = opts.getUrl().toString();
        return url.endsWith("/") ? url : url + "/";
    }

    @Override
    public boolean isDeprecated() {
        return false;
    }

    @Override
    public String getDeprecationMessage() {
        return null;
    }

    @Override
    public String getName() {
        return "changelog";
    }
}
