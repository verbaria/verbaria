package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;

import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;

@SpringBootTest(classes = ItApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PushPullRoundTripIT {

    private static final String PROJECT = "itproj";
    private static final String VERSION = "master";
    private static final String USER = "admin";
    private static final String API_KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Autowired
    ItFixtures fixtures;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    TextFlowRepository textFlowRepository;

    private FileSystem jimfs;
    private Path tmp;

    @AfterEach
    void tearDownFs() throws Exception {
        if (jimfs != null) {
            jimfs.close();
        }
    }

    /** In-memory working dir — all client file I/O is nio Path-based. */
    private Path inMemoryRoot() throws Exception {
        jimfs = Jimfs.newFileSystem(Configuration.unix());
        Path root = jimfs.getPath("/work");
        Files.createDirectories(root);
        return root;
    }

    @Test
    void propertiesSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(PROJECT, VERSION);

        Path src = tmp.resolve("messages.properties");
        Files.writeString(src, "greeting=Hello\nbye=Goodbye\n");

        new PushCommand(pushOpts("source", "properties", PROJECT)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId(PROJECT, VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("Hello", "Goodbye");

        Files.delete(src);
        assertThat(Files.exists(src)).isFalse();

        new PullCommand(pullOpts("source", "properties", PROJECT)).run();

        assertThat(Files.exists(src)).isTrue();
        java.util.Properties p = new java.util.Properties();
        try (var in = Files.newInputStream(src)) {
            p.load(in);
        }
        assertThat(p).as("pulled %s", Files.readString(src)).hasSize(2);
        assertThat(new java.util.HashSet<>(p.values()))
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @Test
    void consuloSubFileRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsulo", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Path anchor = libEn.resolve("my.Localize.yaml");
        Files.writeString(anchor, "greeting:\n    text: Hello\n");
        Path subDir = libEn.resolve("my.Localize");
        Files.createDirectories(subDir);
        Path sub = subDir.resolve("tip.html");
        Files.writeString(sub, "<b>Hi</b>");

        new PushCommand(pushOpts("source", "consulo", "itconsulo")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsulo", VERSION, "my.Localize")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        HTextFlow rawSub = flows.stream()
                .filter(f -> f.getConsuloFileExt() != null).findFirst().orElseThrow();
        assertThat(rawSub.getConsuloFileExt()).isEqualTo("html");
        assertThat(rawSub.getContents().get(0)).isEqualTo("<b>Hi</b>");
        HTextFlow plain = flows.stream()
                .filter(f -> f.getConsuloFileExt() == null).findFirst().orElseThrow();
        assertThat(plain.getContents().get(0)).isEqualTo("Hello");

        Files.delete(sub);
        assertThat(Files.exists(sub)).isFalse();

        new PullCommand(pullOpts("source", "consulo", "itconsulo")).run();

        assertThat(Files.exists(sub)).as("sub-file recreated with same extension").isTrue();
        assertThat(Files.readString(sub)).isEqualTo("<b>Hi</b>");
        assertThat(Files.readString(anchor)).contains("greeting").contains("Hello");
    }

    private static LocaleList enUsLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("en-US"));
        return ll;
    }

    private PushOptionsImpl pushOpts(String pushType, String projectType, String proj)
            throws Exception {
        PushOptionsImpl o = new PushOptionsImpl();
        o.setUrl(URI.create("http://localhost:" + port + "/").toURL());
        o.setProj(proj);
        o.setProjectVersion(VERSION);
        o.setProjectType(projectType);
        o.setSrcDir(tmp);
        o.setTransDir(tmp);
        o.setUsername(USER);
        o.setKey(API_KEY);
        o.setBatchMode(true);
        o.setPushType(pushType);
        o.setIncludes("**");
        o.setLocaleMapList(enUsLocales());
        o.setProjectConfig(tmp.resolve("verbaria.json"));
        return o;
    }

    private PullOptionsImpl pullOpts(String pullType, String projectType, String proj)
            throws Exception {
        PullOptionsImpl o = new PullOptionsImpl();
        o.setUrl(URI.create("http://localhost:" + port + "/").toURL());
        o.setProj(proj);
        o.setProjectVersion(VERSION);
        o.setProjectType(projectType);
        o.setSrcDir(tmp);
        o.setTransDir(tmp);
        o.setUsername(USER);
        o.setKey(API_KEY);
        o.setBatchMode(true);
        o.setPullType(pullType);
        o.setIncludes("**");
        o.setLocaleMapList(enUsLocales());
        o.setProjectConfig(tmp.resolve("verbaria.json"));
        return o;
    }
}
