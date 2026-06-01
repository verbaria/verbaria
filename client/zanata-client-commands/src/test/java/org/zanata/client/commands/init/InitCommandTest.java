package org.zanata.client.commands.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.zanata.client.TestUtils.readFromClasspath;
import static org.zanata.client.commands.Messages.get;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.zanata.client.InMemoryFs;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.ConsoleInteractor;
import org.zanata.client.commands.Messages;
import org.zanata.rest.client.ProjectIterationClient;
import org.zanata.rest.client.RestClientFactory;
import org.zanata.rest.dto.VersionInfo;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import java.nio.file.Paths;
import java.nio.file.Path;

public class InitCommandTest {
    private static final Logger log =
            LoggerFactory.getLogger(InitCommandTest.class);

    @RegisterExtension
    public InMemoryFs tempFolder = new InMemoryFs();

    private InitCommand command;
    private InitOptionsImpl opts;
    @Mock
    private ConsoleInteractor console;
    @Mock
    private RestClientFactory clientFactory;
    @Mock
    private ProjectIterationClient projectIterationClient;

    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        opts = new InitOptionsImpl();
        command = new InitCommand(opts, console, clientFactory);
    }

    @Test
    public void createCommandWithoutMandatoryOptionsWillNotCauseException() {
        // we don't have server url etc yet
        command = new InitCommand(opts, console);
    }

    @Test
    public void willDownloadProjectConfigFromServer() throws IOException {
        when(clientFactory.getProjectIterationClient("gcc", "master")).thenReturn(projectIterationClient);
        when(projectIterationClient.sampleConfiguration()).thenReturn(
                readFromClasspath("serverresponse/projectConfig.json"));

        Path configFileDest = tempFolder.getRoot().resolve("verbaria.json");
        command.downloadProjectConfig("gcc", "master", configFileDest);

        assertThat(Files.exists(configFileDest)).isTrue();
        List<String> lines = Files.readAllLines(configFileDest, Charsets.UTF_8);
        String content = Joiner.on("\n").join(lines);
        assertThat(content).contains("\"project\"");
        assertThat(opts.getProjectConfig()).isEqualTo(configFileDest);
    }

    @Test
    public void willWriteSrcDirIncludesExcludesToConfigFile() throws Exception {
        Path configFile = tempFolder.getRoot().resolve("verbaria.json");
        Files.writeString(configFile,
                readFromClasspath("serverresponse/projectConfig.json"),
                Charsets.UTF_8);

        command.writeToConfig(Paths.get("pot"), null, "",
                Paths.get("po"), configFile);

        List<String> lines = Files.readAllLines(configFile, Charsets.UTF_8);
        StringBuilder content = new StringBuilder();
        for (String line : lines) {
            log.debug(line);
            content.append(line.trim());
        }
        assertThat(content.toString().replace(" ", "")).contains(
                "\"srcDir\":\"pot\"");
        assertThat(content.toString().replace(" ", "")).contains(
                "\"transDir\":\"po\"");
    }

    @Test
    public void willQuitIfServerApiVersionDoesNotSupportInit()
            throws Exception {
        when(clientFactory.getServerVersionInfo()).thenReturn(
                new VersionInfo("3.3.1", "unknown", "unknown"));
        command = new InitCommand(opts, console, clientFactory);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> command.ensureServerVersion());
        assertThat(e.getMessage())
                .contains(Messages.get("server.incompatible"));
    }

    @Test
    public void willQuitIfUsernameAndConfigUnavailable()
            throws Exception {
        opts.setUserConfig(new File("/planet/Mars/verbaria.ini"));
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> command.run());
        assertThat(e.getMessage()).contains(get("missing.user.config"));
    }
}
