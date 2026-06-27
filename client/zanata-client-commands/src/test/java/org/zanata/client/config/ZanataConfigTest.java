package org.zanata.client.config;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.configuration2.CompositeConfiguration;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SystemConfiguration;
import org.apache.commons.configuration2.io.FileHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ZanataConfigTest {
    // Real temp dir: this test drives INIConfiguration (commons-configuration)
    // and jackson, both of which require java.io.File.
    @TempDir
    File tempFolder;
    private final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    File zanataProjectJson;
    File zanataUserFile;

    @BeforeEach
    public void setUp() throws IOException {
        zanataProjectJson = new File(tempFolder, "verbaria.json");
        zanataUserFile = new File(tempFolder, "verbaria.ini");
    }

    @Test
    public void testWriteReadProject() throws Exception {
        writeProject();
        readProject();
    }

    void writeProject() throws Exception {
        ZanataConfig config = new ZanataConfig();
        config.setUrl(new URL("http://example.com"));
        config.setProject("project");
        config.setProjectVersion("version");
        config.getLocales().add(new LocaleMapping("fr", "fr-FR"));
        config.getLocales().add(new LocaleMapping("zh-CN"));
        mapper.writeValue(zanataProjectJson, config);
    }

    void readProject() throws Exception {
        ZanataConfig config =
                mapper.readValue(zanataProjectJson, ZanataConfig.class);
        assertThat(config.getUrl()).isEqualTo(new URL("http://example.com"));
        assertThat(config.getProject()).isEqualTo("project");
        assertThat(config.getProjectVersion()).isEqualTo("version");
        LocaleList locales = config.getLocales();
        assertThat(locales).hasSize(2);
        assertThat(locales.get(0).getLocale()).isEqualTo("fr");
        assertThat(locales.get(0).getMapFrom()).isEqualTo("fr-FR");
        assertThat(locales.get(1).getLocale()).isEqualTo("zh-CN");
        assertThat(locales.get(1).getMapFrom()).isNull();
    }

    @Test
    public void testWriteReadUser() throws Exception {
        writeUser();
        readUser();
    }

    void writeUser() throws Exception {
        INIConfiguration config = new INIConfiguration();
        config.setProperty("zanata.url", new URL("http://zanata.example.com/"));
        config.setProperty("zanata.username", "admin");
        config.setProperty("zanata.key", "b6d7044e9ee3b2447c28fb7c50d86d98");
        config.setProperty("zanata.debug", false);
        config.setProperty("zanata.errors", true);

        FileHandler fh = new FileHandler(config);
        fh.setFile(zanataUserFile);
        fh.save();
    }

    void readUser() throws Exception {
        INIConfiguration ini = new INIConfiguration();
        new FileHandler(ini).load(zanataUserFile);
        CompositeConfiguration config = new CompositeConfiguration();
        config.addConfiguration(new SystemConfiguration());
        config.addConfiguration(ini);
        assertThat(config.getString("zanata.username")).isEqualTo("admin");
        assertThat(config.getBoolean("zanata.debug")).isFalse();
        assertThat(config.getBoolean("zanata.errors")).isTrue();
    }

}
