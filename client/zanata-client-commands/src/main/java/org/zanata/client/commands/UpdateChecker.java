/*
 * Copyright 2013, Red Hat, Inc. and individual contributors
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
package org.zanata.client.commands;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.fedorahosted.openprops.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;

import static org.zanata.client.commands.ConsoleInteractorImpl.AnswerValidator;
import static org.zanata.client.commands.Messages.get;
import static org.zanata.util.VersionUtility.getVersionInfo;

/**
 * This class checks whether there is newer version of client available. It will
 * check a file on disk to determine check frequency and whether should check
 * now. If yes will query OSS sonatype for latest zanata client version and then
 * compare to current version.
 *
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class UpdateChecker {
    private static final Logger log =
            LoggerFactory.getLogger(UpdateChecker.class);
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String OSS_URL =
            "https://oss.sonatype.org/service/local/";
    // update marker file valid properties
    private static final String LAST_CHECKED = "lastChecked";
    private static final String FREQUENCY = "frequency";
    private static final String NO_ASKING = "noAsking";

    private final String sonatypeRestUrl;
    private final ConsoleInteractor console;
    private final String currentVersionNo;
    private final Path updateMarker;

    public UpdateChecker(ConsoleInteractor console) {
        this(OSS_URL, defaultUpdateMarkerFile(), console,
                getVersionInfo(UpdateChecker.class).getVersionNo());
    }

    private static Path defaultUpdateMarkerFile() {
        return Paths.get(System.getProperty("user.home"), ".config",
                "zanata-client-update.properties");
    }

    @VisibleForTesting
    protected UpdateChecker(String sonatypeRestUrl,
            Path updateMarker,
            ConsoleInteractor console, String currentVersionNo) {
        this.sonatypeRestUrl = sonatypeRestUrl;
        this.console = console;
        this.currentVersionNo = currentVersionNo;
        this.updateMarker = updateMarker;
    }

    public boolean needToCheckUpdates(boolean interactiveMode) {
        LocalDate today = LocalDate.now();
        try {
            if (!Files.exists(updateMarker)) {
                createUpdateMarkerFile(updateMarker);
                console.printfln(get("update.marker.created"), updateMarker);
                console.printfln(get("update.marker.hint"));
                return true;
            }
            // read the content and see if we need to check
            Properties props = loadFileToProperties(updateMarker);
            LocalDate lastCheckedDate = readLastCheckedDate(props);
            long daysPassed =
                    ChronoUnit.DAYS.between(lastCheckedDate, today);
            Frequency frequency = readFrequency(props);
            boolean timeToCheck = daysPassed >= frequency.days();
            boolean noAsking = readNoAsking(props);
            if (timeToCheck && !noAsking && interactiveMode) {
                console.printf(get("check.update.yes.no"), (int) daysPassed);
                String check = console.expectAnswerWithRetry(
                        AnswerValidator.YES_NO);
                if (check.toLowerCase().startsWith("n")) {
                    return false;
                }
            }
            return timeToCheck;
        } catch (Exception e) {
            log.debug("Error checking update marker file", e);
            log.warn("Error checking update marker file {}", updateMarker);
            log.warn("Please make sure its permission and content format");
            return false;
        }
    }

    private static LocalDate readLastCheckedDate(Properties props) {
        return LocalDate.parse(props.getProperty(LAST_CHECKED),
                DATE_FORMATTER);
    }

    private static Frequency readFrequency(Properties props) {
        return Frequency.from(props.getProperty(FREQUENCY,
                Frequency.weekly.name()));
    }
    private static boolean readNoAsking(Properties props) {
        return props.getProperty(NO_ASKING, "false").equalsIgnoreCase("true");
    }

    private static Properties loadFileToProperties(Path updateMarker) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(updateMarker);
                Reader reader = new InputStreamReader(in, Charsets.UTF_8)) {
            props.load(reader);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
        return props;
    }

    private static void createUpdateMarkerFile(Path updateMarker)
            throws IOException {
        Path parent = updateMarker.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createFile(updateMarker);
        String today = LocalDate.now().format(DATE_FORMATTER);
        Properties props = new Properties();
        props.setProperty(LAST_CHECKED, today);
        props.setComment(FREQUENCY, get("valid.frequency"));
        props.setProperty(FREQUENCY, "weekly");
        props.setProperty(NO_ASKING, "true");
        props.setComment(NO_ASKING, get("no.check.update.prompt"));
        try (Writer writer = Files.newBufferedWriter(updateMarker,
                Charsets.UTF_8)) {
            props.store(writer, null);
        }
    }

    public void checkNewerVersion() {
        Optional<String> latestVersion = checkLatestVersion(console);
        if (!latestVersion.isPresent()) {
            return;
        }
        if (latestVersion.get().compareTo(currentVersionNo) > 0) {
            console.printfln(get("suggest.update"), latestVersion.get());
        } else {
            console.printfln(get("latest.version.confirm"));
        }
        try {
            Properties props = loadFileToProperties(updateMarker);
            String today = LocalDate.now().format(DATE_FORMATTER);
            props.setProperty(LAST_CHECKED, today);
            try (Writer writer = Files.newBufferedWriter(updateMarker,
                    Charsets.UTF_8)) {
                props.store(writer, null);
            }
        } catch (IOException e) {
            log.warn("failed to load file {}", updateMarker);
        }
    }

    /**
     * This calls oss.sonatype.org's REST api and resolve latest version of
     * client.
     *
     * @return latest version of client in sonatype oss
     */
    private Optional<String> checkLatestVersion(ConsoleInteractor console) {
        String payload;
        try {
            payload = RestClient.builder()
                    .baseUrl(sonatypeRestUrl)
                    .build()
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("artifact/maven/resolve")
                            .queryParam("g", "org.zanata")
                            .queryParam("a", "client")
                            .queryParam("p", "pom")
                            .queryParam("v", "LATEST")
                            .queryParam("r", "releases")
                            .build())
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("Exception when checking updates", e);
            console.printfln(get("check.update.failed"));
            return Optional.absent();
        }
        if (payload == null) {
            console.printfln(get("check.update.failed"));
            return Optional.absent();
        }
        // cheap xml parsing
        String flat = payload.replaceAll("\\n", "");
        Pattern pattern = Pattern.compile("^.+<version>(.+)</version>.+");
        Matcher matcher = pattern.matcher(flat);
        return matcher.matches() ? Optional.of(matcher.group(1)) :
                Optional.absent();
    }

    private enum Frequency {
        weekly, monthly, daily;
        static Frequency from(String value) {
            try {
                return valueOf(value);
            } catch (Exception e) {
                log.warn("unrecognized value [{}]. Fall back to weekly.", value);
                return weekly;
            }
        }
        int days() {
            switch (this) {
                case monthly:
                    return 30;
                case daily:
                    return 1;
                default:
                    return 7;
            }
        }
    }
}
