package org.verbaria.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.yaml.snakeyaml.Yaml;

import org.zanata.client.commands.pull.PullCommand;
import org.zanata.client.commands.push.PushCommand;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

class SourceRoundTripIT extends AbstractPushPullIT {

    private static final String PROJECT = "itproj";

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
        assertThat(p.stringPropertyNames())
                .as("pulled keys must stay human-readable, not the resId hash: %s",
                        Files.readString(src))
                .containsExactlyInAnyOrder("greeting", "bye");
        assertThat(new java.util.HashSet<>(p.values()))
                .containsExactlyInAnyOrder("Hello", "Goodbye");
    }

    @Test
    void gettextSourceRoundTrip() throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itgettext", VERSION);

        Path pot = tmp.resolve("messages.pot");
        Files.writeString(pot, """
                msgid ""
                msgstr ""
                "Content-Type: text/plain; charset=UTF-8\\n"

                msgid "greeting"
                msgstr ""

                msgid "bye"
                msgstr ""
                """);

        new PushCommand(pushOpts("source", "gettext", "itgettext")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itgettext", VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(2);
        assertThat(flows.stream().map(f -> f.getContents().get(0)))
                .containsExactlyInAnyOrder("greeting", "bye");

        Files.delete(pot);
        assertThat(Files.exists(pot)).isFalse();

        new PullCommand(pullOpts("source", "gettext", "itgettext")).run();

        assertThat(Files.exists(pot)).isTrue();
        String content = Files.readString(pot);
        assertThat(content).contains("greeting").contains("bye");
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
        new TransactionTemplate(txManager)
                .executeWithoutResult(s -> {
            List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
            assertThat(flows).hasSize(2);
            HTextFlow rawSub = flows.stream()
                    .filter(f -> extensionStore
                            .get(f, ConsuloSubFile.class)
                            .isPresent())
                    .findFirst().orElseThrow();
            assertThat(extensionStore
                    .get(rawSub, ConsuloSubFile.class)
                    .orElseThrow().getExtension()).isEqualTo("html");
            assertThat(rawSub.getContents().get(0)).isEqualTo("<b>Hi</b>");
            HTextFlow plain = flows.stream()
                    .filter(f -> extensionStore
                            .get(f, ConsuloSubFile.class)
                            .isEmpty())
                    .findFirst().orElseThrow();
            assertThat(plain.getContents().get(0)).isEqualTo("Hello");
        });

        Files.delete(sub);
        assertThat(Files.exists(sub)).isFalse();

        new PullCommand(pullOpts("source", "consulo", "itconsulo")).run();

        assertThat(Files.exists(sub)).as("sub-file recreated with same extension").isTrue();
        assertThat(Files.readString(sub)).isEqualTo("<b>Hi</b>");
        assertThat(Files.readString(anchor)).contains("greeting").contains("Hello");
    }

    @Test
    void consuloYamlIcuMessageFormatRoundTrip() throws Exception {
        String icu = "''''{0}'''' is not compatible with array initializer "
                + "expressions. Use the full form (new int[] '{' ... '}' "
                + "instead of just '{' ... '}')";

        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject("itconsuloicu", VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Path yaml = libEn.resolve("messages.yaml");
        Files.writeString(yaml, "icu.key:\n    text: \"" + icu + "\"\n");

        new PushCommand(pushOpts("source", "consulo", "itconsuloicu")).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId("itconsuloicu", VERSION, "messages")
                .orElseThrow();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
        assertThat(flows).hasSize(1);
        assertThat(flows.get(0).getContents().get(0))
                .as("the icu message format must persist verbatim")
                .isEqualTo(icu);

        Files.writeString(yaml, "icu.key:\n    text: 'placeholder'\n");

        new PullCommand(pullOpts("source", "consulo", "itconsuloicu")).run();

        String raw = Files.readString(yaml);
        assertThat(raw)
                .as("icu apostrophes must stay literal in double-quoted style, "
                        + "not be doubled by single-quoted yaml: %s", raw)
                .contains("text: \"''''{0}''''")
                .doesNotContain("''''''''{0}");

        Object root;
        try (var in = Files.newInputStream(yaml)) {
            root = new Yaml().load(in);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> top = (Map<String, Object>) root;
        @SuppressWarnings("unchecked")
        Map<String, Object> entry = (Map<String, Object>) top.get("icu.key");
        assertThat((String) entry.get("text"))
                .as("the icu format survives the pull verbatim")
                .isEqualTo(icu);
    }

    @Test
    void consuloYamlNamesAndTypesRoundTrip() throws Exception {
        assertParamsRoundTrip("itconsuloparams",
                List.of("kind", "name", "shortcut", "actionName", "shortcutColor"),
                List.of("PsiElement", "String", "Shortcut", "String", "Color"));
    }

    @Test
    void consuloYamlNamesOnlyRoundTrip() throws Exception {
        assertParamsRoundTrip("itconsulonames",
                List.of("kind", "name", "shortcut"), null);
    }

    @Test
    void consuloYamlTypesOnlyRoundTrip() throws Exception {
        assertParamsRoundTrip("itconsulotypes", null,
                List.of("PsiElement", "String", "Color"));
    }

    private void assertParamsRoundTrip(String proj, List<String> names,
            List<String> types) throws Exception {
        tmp = inMemoryRoot();
        fixtures.ensureLocale("en-US");
        fixtures.ensureAdmin(USER, API_KEY);
        fixtures.ensureProject(proj, VERSION);

        Path libEn = tmp.resolve("LOCALIZE-LIB/en_US");
        Files.createDirectories(libEn);
        Path yaml = libEn.resolve("messages.yaml");
        Files.writeString(yaml, paramsYaml(names, types));

        new PushCommand(pushOpts("source", "consulo", proj)).run();

        HDocument doc = documentRepository
                .findByVersionAndDocId(proj, VERSION, "messages").orElseThrow();
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            List<HTextFlow> flows = textFlowRepository.findByDocument(doc.getId());
            assertThat(flows).hasSize(1);
            ConsuloSubFile ext = extensionStore
                    .get(flows.get(0), ConsuloSubFile.class).orElseThrow();
            assertThat(ext.getExtension()).isNull();
            if (names == null) {
                assertThat(ext.getParamNames()).isNullOrEmpty();
            } else {
                assertThat(ext.getParamNames()).isEqualTo(names);
            }
            if (types == null) {
                assertThat(ext.getParamTypes()).isNullOrEmpty();
            } else {
                assertThat(ext.getParamTypes()).isEqualTo(types);
            }
            assertThat(translationEditService.isConsuloFile(flows.get(0))).isFalse();
        });

        Files.writeString(yaml, """
                import.popup.hint.text:
                    text: 'placeholder'
                """);

        new PullCommand(pullOpts("source", "consulo", proj)).run();

        Object root;
        try (var in = Files.newInputStream(yaml)) {
            root = new Yaml().load(in);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> top = (Map<String, Object>) root;
        @SuppressWarnings("unchecked")
        Map<String, Object> entry =
                (Map<String, Object>) top.get("import.popup.hint.text");
        @SuppressWarnings("unchecked")
        List<String> gotNames = (List<String>) entry.get("names");
        @SuppressWarnings("unchecked")
        List<String> gotTypes = (List<String>) entry.get("types");
        assertThat(gotNames).isEqualTo(names);
        assertThat(gotTypes).isEqualTo(types);
    }

    private static String paramsYaml(List<String> names, List<String> types) {
        StringBuilder sb = new StringBuilder();
        sb.append("import.popup.hint.text:\n");
        sb.append("    text: '<html><body><a href=\"action\">{3} {0}...</a> "
                + "{1}? <span style=\"color:{4}\">{2}</span></body></html>'\n");
        appendList(sb, "names", names);
        appendList(sb, "types", types);
        return sb.toString();
    }

    private static void appendList(StringBuilder sb, String field,
            List<String> values) {
        if (values == null) {
            return;
        }
        sb.append("    ").append(field).append(":\n");
        for (String v : values) {
            sb.append("        - ").append(v).append('\n');
        }
    }
}
