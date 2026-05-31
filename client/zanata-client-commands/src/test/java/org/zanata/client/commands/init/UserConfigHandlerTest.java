package org.zanata.client.commands.init;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mockito;
import org.zanata.client.TemporaryFolderExtension;
import org.zanata.client.commands.ConsoleInteractor;
import org.zanata.client.commands.MockConsoleInteractor;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.zanata.client.commands.Messages.get;

public class UserConfigHandlerTest {
    @RegisterExtension
    public TemporaryFolderExtension tempFolder = new TemporaryFolderExtension();
    private InitOptionsImpl opts;
    private UserConfigHandler handler;
    private File userConfig;

    @BeforeEach
    public void setUp() throws IOException {
        opts = new InitOptionsImpl();
        ensureUserConfigExistsWithOneServer();
        ConsoleInteractor console = Mockito.mock(ConsoleInteractor.class);

        handler = new UserConfigHandler(console, opts);

    }

    private void ensureUserConfigExistsWithOneServer() throws IOException {
        userConfig = tempFolder.newFile("verbaria.ini");
        BufferedWriter writer =
                Files.newWriter(userConfig, Charsets.UTF_8);
        PrintWriter printWriter = new PrintWriter(writer);
        printWriter.println("[servers]");
        printWriter.println("a.url=http://localhost:8080/zanata/");
        printWriter.println("a.username=admin");
        printWriter.println("a.key=abcde");
        printWriter.flush();
        printWriter.close();
        opts.setUserConfig(userConfig);
    }

    @Test
    public void exitWhenThereIsNoUserConfig() throws Exception {
        opts.setUserConfig(new File("/planet/Mars/verbaria.ini"));

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> handler.verifyUserConfig());
        assertThat(e.getMessage()).contains(get("missing.user.config"));
    }

    @Test
    public void willExitWhenThereIsNoServerUrlInFile() throws Exception {
        // wipe contents in the file
        BufferedWriter writer =
                Files.newWriter(userConfig, Charsets.UTF_8);
        writer.write("[servers]");
        writer.close();

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> handler.verifyUserConfig());
        assertThat(e.getMessage()).contains(get("missing.server.url"));
    }

    @Test
    public void willUseUserConfigIfThereIsOnlyOneServer() throws Exception {
        handler.verifyUserConfig();
        assertThat(handler.getOpts().getUrl().toString()).isEqualTo("http://localhost:8080/zanata/");
        assertThat(handler.getOpts().getUsername()).isEqualTo("admin");
        assertThat(handler.getOpts().getKey()).isEqualTo("abcde");
    }

    @Test
    public void willAskUserIfUserConfigHasMoreThanOneServerEntries()
            throws Exception {
        Files.append("\nb.url=https://translate.zanata.org\n", userConfig,
                Charsets.UTF_8);
        Files.append("b.username=admin\n", userConfig, Charsets.UTF_8);
        Files.append("b.key=blah\n", userConfig, Charsets.UTF_8);

        ConsoleInteractor console =
                MockConsoleInteractor.predefineAnswers("2");
        handler = new UserConfigHandler(console, opts);

        handler.verifyUserConfig();
        assertThat(handler.getOpts().getUrl().toString())
                .isEqualTo("https://translate.zanata.org");
        assertThat(handler.getOpts().getUsername()).isEqualTo("admin");
        assertThat(handler.getOpts().getKey()).isEqualTo("blah");
    }

}
