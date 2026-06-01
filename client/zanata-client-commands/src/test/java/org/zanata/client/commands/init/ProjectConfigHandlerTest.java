package org.zanata.client.commands.init;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;
import org.zanata.client.InMemoryFs;
import org.zanata.client.commands.ConsoleInteractor;
import org.zanata.client.commands.MockConsoleInteractor;

import static org.assertj.core.api.Assertions.assertThat;

public class ProjectConfigHandlerTest {
    @RegisterExtension
    public InMemoryFs tempFolder = new InMemoryFs();

    @Test
    public void willBackupExistingProjectConfig() throws Exception {
        ConsoleInteractor console =
                MockConsoleInteractor.predefineAnswers("y");
        InitOptions opts = new InitOptionsImpl();
        ProjectConfigHandler handler = new ProjectConfigHandler(console, opts);
        Path projectConfig = tempFolder.newFile("verbaria.json");
        opts.setProjectConfig(projectConfig);

        handler.handleExistingProjectConfig();

        List<String> capturedPrompts =
                MockConsoleInteractor.getCapturedPrompts(console);
        String lastMessage = capturedPrompts.get(capturedPrompts.size() - 1);
        assertThat(lastMessage).contains("Old project config has been renamed to ");

        String backupPath =
                lastMessage.replace("Old project config has been renamed to ", "");
        assertThat(Files.exists(tempFolder.getRoot().getFileSystem()
                .getPath(backupPath))).isTrue();
        assertThat(opts.getProj()).isNull();
        assertThat(opts.getProjectVersion()).isNull();
        assertThat(opts.getProjectType()).isNull();
        assertThat(opts.getProjectConfig()).isNull();
        assertThat(opts.getSrcDir()).isNull();
        assertThat(opts.getTransDir()).isNull();
        assertThat(opts.getIncludes()).isEmpty();
        assertThat(opts.getExcludes()).isEmpty();
    }

}
