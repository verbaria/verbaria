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

import org.kohsuke.args4j.Option;
import org.zanata.client.commands.BasicOptionsImpl;
import org.zanata.client.commands.ZanataCommand;

/**
 * Options for the offline {@code changelog} command, which diffs two
 * {@code verbaria-lock.json} files and renders the change set as a git commit
 * message (default) or a Markdown changelog.
 */
public class ChangelogOptionsImpl extends BasicOptionsImpl
        implements ChangelogOptions {

    private File oldLock;
    private File newLock;
    private String format = "git-commit";
    private File output;

    @Override
    public File getOldLock() {
        return oldLock;
    }

    @Option(name = "--old", metaVar = "FILE",
            usage = "Previous verbaria-lock.json to compare against. "
                    + "If omitted or missing, every translation is treated as new.")
    public void setOldLock(File oldLock) {
        this.oldLock = oldLock;
    }

    @Override
    public File getNewLock() {
        return newLock;
    }

    @Option(name = "--new", metaVar = "FILE", required = true,
            usage = "Current verbaria-lock.json (the freshly pulled state).")
    public void setNewLock(File newLock) {
        this.newLock = newLock;
    }

    @Override
    public String getFormat() {
        return format;
    }

    @Option(name = "--format", metaVar = "FORMAT",
            usage = "Output format: git-commit (commit message with "
                    + "Co-authored-by trailers, default) or markdown "
                    + "(changelog for a PR body).")
    public void setFormat(String format) {
        this.format = format;
    }

    @Override
    public File getOutput() {
        return output;
    }

    @Option(name = "--output", aliases = "-o", metaVar = "FILE",
            usage = "Write the output to FILE instead of standard output.")
    public void setOutput(File output) {
        this.output = output;
    }

    @Override
    public ZanataCommand initCommand() {
        return new ChangelogCommand(this);
    }

    @Override
    public String getCommandName() {
        return "changelog";
    }

    @Override
    public String getCommandDescription() {
        return "Generates a translation changelog by diffing two verbaria-lock.json "
                + "files. Outputs a git commit message (what changed and by "
                + "whom, with Co-authored-by trailers) or a Markdown changelog.";
    }
}
