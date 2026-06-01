package org.zanata.client.commands;

import java.io.IOException;
import java.net.URL;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.zanata.client.commands.Messages.get;

public class PutUserOptionsImplTest {

    private PutUserOptionsImpl opts;

    @BeforeEach
    public void setUp() throws IOException {
        MockitoAnnotations.initMocks(this);
        opts = new PutUserOptionsImpl();
        opts.setUsername("jcitizen");
        opts.setKey("1234567890");
        opts.setUrl(new URL("http://localhost:8080/zanata/"));
    }

    @Test
    public void testValidEnabledOptions() throws Exception {
        opts.setUserEnabled("true");
        assertThat(opts.isUserEnabled()).isEqualTo("true");
        opts.setUserEnabled("false");
        assertThat(opts.isUserEnabled()).isEqualTo("false");
        opts.setUserEnabled("auto");
        assertThat(opts.isUserEnabled()).isEqualTo("auto");
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> opts.setUserEnabled("invalid"));
        assertThat(e.getMessage())
                .contains("--user-enabled requires true or false (or auto)");
    }

    @Test
    public void testUserKeyCannotBeBlank() {
        opts.setUserKey("   ");
        assertThat(opts.getUserKey()).isNull();
    }

    @Test
    public void testCommandDescription() {
        assertThat(opts.getCommandName()).isEqualTo("put-user");
        assertThat(opts.getCommandDescription()).isEqualTo(get("command.description.put-user"));
    }
}
