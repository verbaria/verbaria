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
import org.zanata.client.commands.ZanataCommand;
import org.zanata.client.lock.LockChangelog;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;

/**
 * Offline command: diffs two {@code verbaria.lock} files and writes the change
 * set (git commit message, or Markdown changelog) to stdout or a file. Needs no
 * server connection. An empty diff produces empty output, so the scheduled-sync
 * workflow can gate the commit/PR on a non-empty result.
 */
public class ChangelogCommand implements ZanataCommand {
    private static final Logger log =
            LoggerFactory.getLogger(ChangelogCommand.class);

    private final ChangelogOptions opts;

    public ChangelogCommand(ChangelogOptions opts) {
        this.opts = opts;
    }

    @Override
    public void runWithActions() throws Exception {
        VerbariaLock oldLock =
                VerbariaLockReaderWriter.readOrEmpty(opts.getOldLock());
        File newFile = opts.getNewLock();
        if (newFile == null || !newFile.isFile()) {
            throw new RuntimeException("New lock file not found: " + newFile);
        }
        VerbariaLock newLock = VerbariaLockReaderWriter.readOrNull(newFile);

        LockChangelog.Format format =
                LockChangelog.Format.parse(opts.getFormat());
        String output = LockChangelog.render(oldLock, newLock, format);

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
