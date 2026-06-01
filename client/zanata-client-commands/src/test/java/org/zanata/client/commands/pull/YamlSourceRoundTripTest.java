package org.zanata.client.commands.pull;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full source round-trip for the consulo/YAML project type:
 * <pre>
 *   push (read en_US source file)  →  server (hash id, key kept in gettext
 *   context)  →  external edit on the server  →  pull (write source back)
 * </pre>
 * Drives the real push reader ({@code push.YamlStrategy.loadSrcDoc}) and the
 * real pull writer ({@code pull.YamlStrategy.writeSrcFile}); only the network
 * hop is simulated by transforming the pushed {@link Resource} the way the
 * server does. Asserts the externally-changed value ends up back in the file.
 */
public class YamlSourceRoundTripTest {

    private static final String DOC = "Foo.BarLocalize";

    private FileSystem fs;

    @AfterEach
    void closeFs() throws Exception {
        if (fs != null) {
            fs.close();
        }
    }

    /** Fresh in-memory working directory; no real folders are created. */
    private Path inMemoryRoot() throws Exception {
        fs = Jimfs.newFileSystem(Configuration.unix());
        Path root = fs.getPath("/work");
        Files.createDirectories(root);
        return root;
    }

    @Test
    public void externalEditComesBackOnPull() throws Exception {
        Path repo = inMemoryRoot();
        Path srcFile = repo.resolve(
                "modules/x/src/main/resources/LOCALIZE-LIB/en_US/" + DOC + ".yaml");
        Files.createDirectories(srcFile.getParent());
        Files.writeString(srcFile, """
                greeting.bye:
                    text: Bye
                greeting.hello:
                    text: Hello
                """, StandardCharsets.UTF_8);

        // 1) PUSH — real push reader turns the file into a Resource payload.
        Resource pushed = readSource(repo);
        assertThat(pushed.getTextFlows()).hasSize(2);

        // 2) SERVER — store it the way the server does: hash the id, keep the
        //    original key in the gettext context.
        Resource onServer = toServerForm(pushed);

        // 3) EXTERNAL CHANGE — someone edits "Hello" in the web editor.
        setContentForKey(onServer, "greeting.hello", "Hello, world!");

        // 4) PULL — real pull writer rewrites the on-disk source file.
        PullOptionsImpl pullOpts = new PullOptionsImpl();
        pullOpts.setProjectType("consulo");
        pullOpts.setSrcDir(repo);
        new YamlStrategy(pullOpts).writeSrcFile(onServer);

        // 5) ASSERT — the external change is now in the code.
        String result = Files.readString(srcFile, StandardCharsets.UTF_8);
        assertThat(result).contains("greeting.hello:");
        assertThat(result).contains("Hello, world!");   // the external edit
        assertThat(result).contains("greeting.bye:");
        assertThat(result).contains("Bye");             // untouched entry
        assertThat(result).doesNotContain("text: Hello\n"); // old value replaced
    }

    @Test
    public void rawSubFileExternalEditComesBackOnPull() throws Exception {
        Path repo = inMemoryRoot();
        // A directory doc whose entries are whole raw files (any extension).
        Path docDir = repo.resolve(
                "modules/y/src/main/resources/LOCALIZE-LIB/en_US/Foo.SubLocalize");
        Files.createDirectories(docDir.resolve("inspections"));
        Path html = docDir.resolve("inspections/MyInspection.html");
        Files.writeString(html, "<html>original</html>",
                StandardCharsets.UTF_8);

        // 1) PUSH — real reader; the raw file becomes a text flow keyed by its
        //    sub-path, carrying just its extension in the consulo extension.
        Resource pushed = readSource(repo, "Foo.SubLocalize", "**/*");
        assertThat(pushed.getTextFlows()).hasSize(1);
        TextFlow pushedTf = pushed.getTextFlows().get(0);
        assertThat(pushedTf.getId()).isEqualTo("inspections.MyInspection");
        ConsuloSubFile pushedCf = pushedTf.getExtensions()
                .findByType(ConsuloSubFile.class);
        assertThat(pushedCf).isNotNull();
        assertThat(pushedCf.getExtension()).isEqualTo("html");

        // 2) SERVER — keep id-hash, context, and consulo extension.
        Resource onServer = toServerForm(pushed);

        // 3) EXTERNAL CHANGE — someone edits the file body in the web editor.
        setContentForKey(onServer, "inspections.MyInspection",
                "<html>edited on server</html>");

        // 4) PULL — real writer recreates the exact raw file.
        PullOptionsImpl pullOpts = new PullOptionsImpl();
        pullOpts.setProjectType("consulo");
        pullOpts.setSrcDir(repo);
        new YamlStrategy(pullOpts).writeSrcFile(onServer);

        // 5) ASSERT — the file is still a .html with the edited body, not yaml.
        String result = Files.readString(html, StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("<html>edited on server</html>");
        assertThat(result).doesNotContain("text:");
    }

    private static Resource readSource(Path repo) throws Exception {
        return readSource(repo, DOC, "**/*.yaml");
    }

    private static Resource readSource(Path repo, String doc, String includes)
            throws Exception {
        PushOptionsImpl push = new PushOptionsImpl();
        push.setProjectType("consulo");
        push.setSrcDir(repo);
        push.setSourceLang("en-US");
        push.setIncludes(includes);
        push.setExcludes("");
        push.setDefaultExcludes(true);
        push.setCaseSensitive(true);
        push.setLocaleMapList(new org.zanata.client.config.LocaleList());
        org.zanata.client.commands.push.YamlStrategy strat =
                new org.zanata.client.commands.push.YamlStrategy();
        strat.setPushOptions(push);
        strat.init();
        return strat.loadSrcDoc(repo, doc);
    }

    /**
     * Mimic the server: resId is a hash; the original key goes to the context
     * and any push-supplied consulo file path (raw sub-file) is kept.
     */
    private static Resource toServerForm(Resource pushed) {
        Resource server = new Resource(pushed.getName());
        server.setLang(new LocaleId("en-US"));
        for (TextFlow pf : pushed.getTextFlows()) {
            String key = pf.getId(); // push uses the raw key as the id
            String content = pf.getContents().get(0);
            TextFlow sf = new TextFlow("hash-" + key, new LocaleId("en-US"),
                    content);
            PotEntryHeader peh = new PotEntryHeader();
            peh.setContext(key);
            sf.getExtensions(true).add(peh);
            ConsuloSubFile pushedCf = pf.getExtensions() == null ? null
                    : pf.getExtensions().findByType(ConsuloSubFile.class);
            if (pushedCf != null) {
                sf.getExtensions(true).add(
                        new ConsuloSubFile(pushedCf.getExtension()));
            }
            server.getTextFlows().add(sf);
        }
        return server;
    }

    private static void setContentForKey(Resource res, String key,
            String content) {
        for (TextFlow tf : res.getTextFlows()) {
            PotEntryHeader peh = tf.getExtensions(true)
                    .findByType(PotEntryHeader.class);
            if (peh != null && key.equals(peh.getContext())) {
                tf.setContents(List.of(content));
                return;
            }
        }
        throw new AssertionError("key not found: " + key);
    }
}
