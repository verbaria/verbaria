package org.zanata.client.commands.pull;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.zanata.client.config.LocaleMapping;
import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;

/**
 * A consulo raw sub-file (a whole file, carrying a {@link ConsuloSubFile}
 * extension) must round-trip as its own translated file on a trans pull — the
 * same shape as the source — rather than being inlined as a {@code key: text}
 * yaml entry.
 */
class ConsuloStrategyPullTransTest {

    @TempDir
    Path repo;

    @Test
    void rawSubFileTranslationIsWrittenAsSeparateFileNotInlined() throws Exception {
        Resource source = new Resource("Foo.SubLocalize");
        source.setLang(new LocaleId("en-US"));
        TextFlow tf = new TextFlow("hash-1", new LocaleId("en-US"),
                "<html>source</html>");
        PotEntryHeader peh = new PotEntryHeader();
        peh.setContext("inspections.MyInspection");
        tf.getExtensions(true).add(peh);
        tf.getExtensions(true).add(new ConsuloSubFile("html"));
        source.getTextFlows().add(tf);

        TranslationsResource trans = new TranslationsResource();
        TextFlowTarget t = new TextFlowTarget("hash-1");
        t.setContents(List.of("<html>translated</html>"));
        t.setState(ContentState.Translated);
        trans.getTextFlowTargets().add(t);

        PullOptionsImpl opts = new PullOptionsImpl();
        opts.setProjectType("consulo");
        opts.setSrcDir(repo);
        opts.setTransDir(repo);

        LocaleMappedTranslatedDoc doc = new LocaleMappedTranslatedDoc(
                source, trans, new LocaleMapping("fr-FR"));

        new ConsuloStrategy(opts).writeTransFile("Foo.SubLocalize", doc);

        Path htmlFile;
        try (Stream<Path> walk = Files.walk(repo)) {
            htmlFile = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("MyInspection.html"))
                    .findFirst().orElse(null);
        }
        assertThat(htmlFile)
                .as("translated raw sub-file must be its own file, not inlined yaml")
                .isNotNull();
        assertThat(Files.readString(htmlFile, StandardCharsets.UTF_8))
                .isEqualTo("<html>translated</html>");

        boolean inlinedYaml;
        try (Stream<Path> walk = Files.walk(repo)) {
            inlinedYaml = walk.filter(Files::isRegularFile).anyMatch(p -> {
                try {
                    return Files.readString(p).contains("text:");
                } catch (Exception e) {
                    return false;
                }
            });
        }
        assertThat(inlinedYaml)
                .as("sub-file translation must not be inlined as a yaml text entry")
                .isFalse();
    }

    @Test
    void subFileRemovedFromServerIsDeletedOnTransPull() throws Exception {
        Path inspections = repo.resolve("fr-FR/Foo.SubLocalize/inspections");
        Files.createDirectories(inspections);
        Path keep = inspections.resolve("Keep.html");
        Path gone = inspections.resolve("Gone.html");
        Files.writeString(keep, "<html>old</html>", StandardCharsets.UTF_8);
        Files.writeString(gone, "<html>stale</html>", StandardCharsets.UTF_8);

        Resource source = new Resource("Foo.SubLocalize");
        source.setLang(new LocaleId("en-US"));
        TextFlow tf = new TextFlow("hash-keep", new LocaleId("en-US"),
                "<html>source</html>");
        PotEntryHeader peh = new PotEntryHeader();
        peh.setContext("inspections.Keep");
        tf.getExtensions(true).add(peh);
        tf.getExtensions(true).add(new ConsuloSubFile("html"));
        source.getTextFlows().add(tf);

        TranslationsResource trans = new TranslationsResource();
        TextFlowTarget t = new TextFlowTarget("hash-keep");
        t.setContents(List.of("<html>translated</html>"));
        t.setState(ContentState.Translated);
        trans.getTextFlowTargets().add(t);

        PullOptionsImpl opts = new PullOptionsImpl();
        opts.setProjectType("consulo");
        opts.setSrcDir(repo);
        opts.setTransDir(repo);

        new ConsuloStrategy(opts).writeTransFile("Foo.SubLocalize",
                new LocaleMappedTranslatedDoc(source, trans,
                        new LocaleMapping("fr-FR")));

        assertThat(Files.exists(gone))
                .as("a sub-file removed from the DB must be deleted on trans pull")
                .isFalse();
        assertThat(Files.readString(keep, StandardCharsets.UTF_8))
                .isEqualTo("<html>translated</html>");
    }
}
