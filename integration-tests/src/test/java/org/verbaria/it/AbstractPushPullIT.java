package org.verbaria.it;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.pull.PullOptionsImpl;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;

import org.verbaria.server.headless.service.OfflineExportService;
import org.verbaria.server.headless.service.TranslationEditService;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

@SpringBootTest(classes = ItApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractPushPullIT {

    static final String VERSION = "master";
    static final String USER = "admin";
    static final String API_KEY = "0123456789abcdef0123456789abcdef";

    @LocalServerPort
    int port;

    @Autowired
    ItFixtures fixtures;
    @Autowired
    DocumentRepository documentRepository;
    @Autowired
    TextFlowRepository textFlowRepository;
    @Autowired
    TextFlowTargetRepository textFlowTargetRepository;
    @Autowired
    TextFlowTargetHistoryRepository textFlowTargetHistoryRepository;
    @Autowired
    TranslationEditService translationEditService;
    @Autowired
    OfflineExportService offlineExportService;
    @Autowired
    TextFlowExtensionStore extensionStore;
    @Autowired
    PlatformTransactionManager txManager;

    final ObjectMapper json = new ObjectMapper();

    FileSystem jimfs;
    Path tmp;

    @AfterEach
    void tearDownFs() throws Exception {
        if (jimfs != null) {
            jimfs.close();
        }
    }

    /** In-memory working dir — all client file I/O is nio Path-based. */
    Path inMemoryRoot() throws Exception {
        jimfs = Jimfs.newFileSystem(Configuration.unix());
        Path root = jimfs.getPath("/work");
        Files.createDirectories(root);
        return root;
    }

    static LocaleList frLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("fr-FR"));
        return ll;
    }

    static LocaleList enUsLocales() {
        LocaleList ll = new LocaleList();
        ll.add(new LocaleMapping("en-US"));
        return ll;
    }

    PushOptionsImpl pushOpts(String pushType, String projectType, String proj)
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

    PullOptionsImpl pullOpts(String pullType, String projectType, String proj)
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

    void pushBoth(String proj) throws Exception {
        PushOptionsImpl push = pushOpts("both", "properties", proj);
        push.setLocaleMapList(frLocales());
        push.setIncludes("messages.properties");
        new PushCommand(push).run();
    }

    /** Pull (both) and return the lock the CLI wrote, like translate-sync does. */
    VerbariaLock pullBothAndReadLock(String proj) throws Exception {
        PullOptionsImpl pull = pullOpts("both", "properties", proj);
        pull.setLocaleMapList(frLocales());
        pull.setIncludes("messages.properties");
        new PullCommand(pull).run();
        return VerbariaLockReaderWriter.readOrNull(
                tmp.resolve("verbaria-lock.json"));
    }

    int approve(Long tfId, String content) throws Exception {
        Map<String, Object> body = Map.of(
                "id", tfId,
                "translations", List.of(content),
                "status", "Approved");
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/rest/trans/fr-FR"))
                .header("X-Auth-User", USER)
                .header("X-Auth-Token", API_KEY)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(
                        json.writeValueAsString(body)))
                .build();
        HttpResponse<String> resp = HttpClient.newHttpClient()
                .send(req, HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }
}
