package org.zanata.client.commands.pull;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that pulling source for the consulo/YAML project type rewrites the
 * existing {@code LOCALIZE-LIB/en_US/<doc>.yaml} file from the server's
 * (possibly edited) source — the round-trip that {@code writeSrcFile} enables.
 */
public class ConsuloStrategyPullSrcTest {

    private static TextFlow tf(String id, String key, String content) {
        TextFlow tf = new TextFlow(id, new LocaleId("en-US"), content);
        PotEntryHeader peh = new PotEntryHeader();
        peh.setContext(key); // server carries the original key here
        tf.getExtensions(true).add(peh);
        return tf;
    }

    /** A raw sub-file flow: key carries the sub-path; the consulo extension
     *  carries only the file extension. */
    private static TextFlow tfRaw(String id, String key, String ext,
            String content) {
        TextFlow tf = new TextFlow(id, new LocaleId("en-US"), content);
        PotEntryHeader peh = new PotEntryHeader();
        peh.setContext(key);
        tf.getExtensions(true).add(peh);
        tf.getExtensions(true).add(new ConsuloSubFile(ext));
        return tf;
    }

    private static Resource sourceDoc(String name) {
        Resource doc = new Resource(name);
        doc.setLang(new LocaleId("en-US"));
        return doc;
    }

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

    private static ConsuloStrategy strategy(Path srcDir) {
        PullOptionsImpl opts = new PullOptionsImpl();
        opts.setProjectType("consulo");
        opts.setSrcDir(srcDir);
        return new ConsuloStrategy(opts);
    }

    @Test
    public void rewritesExistingSourceFileFromServer() throws Exception {
        Path tmp = inMemoryRoot();
        Path srcFile = tmp.resolve(
                "modules/a/src/main/resources/LOCALIZE-LIB/en_US/Foo.BarLocalize.yaml");
        Files.createDirectories(srcFile.getParent());
        // Stale on-disk content; the pull should overwrite it.
        Files.writeString(srcFile, "action.filters.text:\n    text: Old\n",
                StandardCharsets.UTF_8);

        Resource doc = sourceDoc("Foo.BarLocalize");
        doc.getTextFlows().add(tf("h1", "action.filters.text", "Filters"));
        doc.getTextFlows().add(tf("h2", "build.event.message.at", "At {0}"));

        strategy(tmp).writeSrcFile(doc);

        String written = Files.readString(srcFile, StandardCharsets.UTF_8);
        // key: { text: value } shape, with the server's (edited) value
        assertThat(written).contains("action.filters.text:");
        assertThat(written).contains("text: Filters");
        assertThat(written).contains("build.event.message.at:");
        assertThat(written).contains("At {0}");
        // 4-space indented nested map, no longer the stale "Old"
        assertThat(written).contains("\n    text:");
        assertThat(written).doesNotContain("Old");
        // keys are written sorted
        assertThat(written.indexOf("action.filters.text"))
                .isLessThan(written.indexOf("build.event.message.at"));
    }

    @Test
    public void skipsWhenNoExistingSourceFile() throws Exception {
        Path tmp = inMemoryRoot();

        Resource doc = sourceDoc("No.SuchLocalize");
        doc.getTextFlows().add(tf("h1", "some.key", "Value"));

        // Must not throw and must not scatter any new files.
        strategy(tmp).writeSrcFile(doc);

        try (Stream<Path> walk = Files.walk(tmp)) {
            assertThat(walk.anyMatch(p ->
                    p.toString().contains("LOCALIZE-LIB"))).isFalse();
        }
    }

    @Test
    public void rewritesRawSubFilesAsFilesNotYaml() throws Exception {
        Path tmp = inMemoryRoot();
        // The doc is a DIRECTORY of raw sub-files (e.g. html templates).
        Path docDir = tmp.resolve(
                "modules/a/src/main/resources/LOCALIZE-LIB/en_US/Foo.BarLocalize");
        Files.createDirectories(docDir.resolve("inspections"));
        Path htmlFile = docDir.resolve("inspections/MyInspection.html");
        Files.writeString(htmlFile, "<html>old body</html>",
                StandardCharsets.UTF_8);

        // Server carries the raw body keyed by its sub-path (push convention:
        // extension stripped, '/' -> '.').
        Resource doc = sourceDoc("Foo.BarLocalize");
        doc.getTextFlows().add(tf("h1", "inspections.MyInspection",
                "<html>new body</html>"));

        strategy(tmp).writeSrcFile(doc);

        // The raw file must stay a raw file with the new body verbatim ...
        String written = Files.readString(htmlFile, StandardCharsets.UTF_8);
        assertThat(written).isEqualTo("<html>new body</html>");
        // ... and we must NOT have yaml-ified it into key: {text: ...}.
        assertThat(written).doesNotContain("text:");
        // No stray <doc>.yaml created next to the directory.
        try (Stream<Path> walk = Files.walk(docDir.getParent())) {
            assertThat(walk.anyMatch(p ->
                    p.getFileName().toString()
                            .equalsIgnoreCase("Foo.BarLocalize.yaml")))
                    .isFalse();
        }
    }

    @Test
    public void rewritesRawSubFilesByExtensionPreservingPaths()
            throws Exception {
        Path tmp = inMemoryRoot();
        Path docDir = tmp.resolve(
                "modules/a/src/main/resources/LOCALIZE-LIB/en_US/Foo.BarLocalize");
        Files.createDirectories(docDir.resolve("inspections"));
        // One file already exists; gets an edited body back.
        Path html = docDir.resolve("inspections/MyInspection.html");
        Files.writeString(html, "<html>old</html>", StandardCharsets.UTF_8);

        Resource doc = sourceDoc("Foo.BarLocalize");
        // Edited existing file — path from key, extension from the consulo ext.
        doc.getTextFlows().add(tfRaw("h1", "inspections.MyInspection",
                "html", "<html>new</html>"));
        // A brand-new file that is NOT yet on disk — must still be created,
        // path rebuilt from the key, extension from the consulo ext.
        doc.getTextFlows().add(tfRaw("h2", "colors.Theme",
                "colorPage", "theme body"));

        strategy(tmp).writeSrcFile(doc);

        assertThat(Files.readString(html, StandardCharsets.UTF_8))
                .isEqualTo("<html>new</html>");
        Path created = docDir.resolve("colors/Theme.colorPage");
        assertThat(Files.exists(created)).isTrue();
        assertThat(Files.readString(created, StandardCharsets.UTF_8))
                .isEqualTo("theme body");
        // Never yaml-ified.
        assertThat(Files.readString(html, StandardCharsets.UTF_8))
                .doesNotContain("text:");
    }

    @Test
    public void rewritesSingleRawFileAsRawBody() throws Exception {
        Path tmp = inMemoryRoot();
        // The doc is a single raw file (not yaml): keep it raw on the way back.
        Path raw = tmp.resolve(
                "modules/a/src/main/resources/LOCALIZE-LIB/en_US/Readme.colorPage");
        Files.createDirectories(raw.getParent());
        Files.writeString(raw, "old raw content", StandardCharsets.UTF_8);

        Resource doc = sourceDoc("Readme");
        doc.getTextFlows().add(tf("h1", "content", "new raw content"));

        strategy(tmp).writeSrcFile(doc);

        String written = Files.readString(raw, StandardCharsets.UTF_8);
        assertThat(written).isEqualTo("new raw content");
        assertThat(written).doesNotContain("text:");
    }

    @Test
    public void ignoresTargetBuildOutputs() throws Exception {
        Path tmp = inMemoryRoot();
        // Only a build-output copy exists under target/ — must be ignored.
        Path targetCopy = tmp.resolve(
                "modules/a/target/classes/LOCALIZE-LIB/en_US/Foo.BarLocalize.yaml");
        Files.createDirectories(targetCopy.getParent());
        Files.writeString(targetCopy, "x:\n    text: stale\n",
                StandardCharsets.UTF_8);

        Resource doc = sourceDoc("Foo.BarLocalize");
        doc.getTextFlows().add(tf("h1", "x", "New"));

        strategy(tmp).writeSrcFile(doc);

        // The target/ copy must be untouched.
        assertThat(Files.readString(targetCopy, StandardCharsets.UTF_8))
                .contains("stale").doesNotContain("New");
    }
}
