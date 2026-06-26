/*
 * Copyright 2014, Red Hat, Inc. and individual contributors
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
package org.zanata.client.commands.init;

import static org.apache.commons.io.Charsets.UTF_8;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Confirmation;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Hint;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Warning;
import static org.zanata.client.commands.Messages.get;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.ConfigurableCommand;
import org.zanata.client.commands.ConsoleInteractor;
import org.zanata.client.commands.ConsoleInteractorImpl;
import org.zanata.client.commands.ServerRestClient;
import org.zanata.client.exceptions.ConfigException;
import org.zanata.client.util.VersionComparator;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

public class InitCommand extends ConfigurableCommand<InitOptions> {
    private static final Logger log = LoggerFactory
            .getLogger(InitCommand.class);
    private static final String ITERATION_URL = "%siteration/view/%s/%s";
    private ConsoleInteractor console;
    private ProjectConfigHandler projectConfigHandler;
    private UserConfigHandler userConfigHandler;

    public InitCommand(InitOptions opts) {
        super(opts);
        console = new ConsoleInteractorImpl(opts);
        projectConfigHandler = new ProjectConfigHandler(console, getOpts());
        userConfigHandler = new UserConfigHandler(console, getOpts());
    }

    @VisibleForTesting
    protected InitCommand(InitOptions opts, ConsoleInteractor console) {
        super(opts);
        this.console = console;
        projectConfigHandler = new ProjectConfigHandler(console, getOpts());
        userConfigHandler = new UserConfigHandler(console, getOpts());
    }

    @Override
    protected void run() throws Exception {
        if (getOpts().getUsername() == null) {
            log.info("Username not specified, trying config file");
            userConfigHandler.verifyUserConfig();
        }

        ensureServerVersion();

        projectConfigHandler.handleExistingProjectConfig();

        if (getOpts().getProj() == null
                || getOpts().getProjectVersion() == null) {
            throw new ConfigException(
                    "Project and version must be specified (use --project and"
                            + " --project-version); create them in the server UI"
                            + " first.");
        }

        downloadProjectConfig(getOpts().getProj(),
                getOpts().getProjectVersion(), Paths.get("verbaria.json"));

        displayAdviceAboutWhatIsNext(projectConfigHandler.hasOldConfig());
    }

    @VisibleForTesting
    protected void ensureServerVersion() {
        String serverVersion = ServerRestClient.serverVersion(
                getOpts().getUrl(), getOpts().getUsername(), getOpts().getKey());
        if (serverVersion != null
                && new VersionComparator().compare(serverVersion, "3.4.0") < 0) {
            console.printfln(Warning, get("server.incompatible"));
            console.printfln(Hint, get("server.incompatible.hint"));
            throw new RuntimeException(get("server.incompatible"));
        }
    }

    private void displayAdviceAboutWhatIsNext(boolean hasOldConfig) {
        console.printfln(get("what.next"));
        if (hasOldConfig) {
            console.printfln(get("compare.project.config"),
                    projectConfigHandler.getBackup());
        }
        console.printfln(get("view.project"),
                getProjectIterationUrl(getOpts().getUrl(), getOpts().getProj(),
                        getOpts().getProjectVersion()));
        if (isInvokedByMaven()) {
            console.printfln(get("mvn.push.source"));
            console.printfln(get("mvn.push.both"));
            console.printfln(get("mvn.push.trans"));
            console.printfln(get("mvn.help"));
        } else {
            console.printfln(get("cli.push.source"));
            console.printfln(get("cli.push.both"));
            console.printfln(get("cli.push.trans"));
            console.printfln(get("cli.help"));
        }
        console.printfln(get("browse.online.help"));
    }

    private boolean isInvokedByMaven() {
        return getOpts().getClass().getPackage().getName().contains("maven");
    }

    private static String getProjectIterationUrl(URL server, String projectSlug,
            String iterationSlug) {
        return String.format(ITERATION_URL, server, projectSlug, iterationSlug);
    }

    @VisibleForTesting
    protected void downloadProjectConfig(String projectId, String iterationId,
            Path configFileDest) throws IOException {
        String content = ServerRestClient.fetchConfig(getOpts().getUrl(),
                getOpts().getUsername(), getOpts().getKey(), projectId,
                iterationId);

        Preconditions.checkState(!Files.exists(configFileDest),
                "Can not create %s. Make sure permission is writable.",
                configFileDest);

        Files.writeString(configFileDest, content, UTF_8);
        getOpts().setProjectConfig(configFileDest);
        console.printfln(Confirmation, "Project config created at:%s",
                getOpts().getProjectConfig());
    }
}
