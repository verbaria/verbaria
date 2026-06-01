package org.zanata.client.commands;


import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zanata.client.commands.push.PushOptionsImpl;
import org.zanata.client.config.FileMappingRule;
import org.zanata.client.config.LocaleMapping;



import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Paths;
import java.nio.file.Path;

public class TransFileResolverTest {

    private TransFileResolver resolver;
    private ConfigurableProjectOptions opts;

    @BeforeEach
    public void setUp() {
        opts = new PushOptionsImpl();
        resolver = new TransFileResolver(opts);
    }

    @Test
    public void canGetTransFileUsingRule() {
        opts.setTransDir(Paths.get("."));
        opts.setProjectType("podir");
        opts.setFileMappingRules(Lists.newArrayList(
            new FileMappingRule("**/*.pot",
                "{path}/{locale_with_underscore}.po"),
            new FileMappingRule("**/*.properties",
                "{path}/{filename}_{locale_with_underscore}.{extension}")));
        Path gettext =
            resolver.resolveTransFile(DocNameWithExt.from(
                    "gcc/po/gcc.pot"), new LocaleMapping("de-DE"), Optional
                .<String>absent());

        assertThat(gettext.toString()).isEqualTo("./gcc/po/de_DE.po");

        Path prop = resolver
            .resolveTransFile(DocNameWithExt.from(
                            "src/main/resources/messages.properties"),
                    new LocaleMapping("zh"), Optional.<String>absent());
        assertThat(prop.toString()).isEqualTo("./src/main/resources/messages_zh.properties");
    }

    @Test
    public void canGetTransFileUsingProjectTypeIfNoRuleIsApplicable() {
        opts.setTransDir(Paths.get("."));
        opts.setProjectType("file");
        Path noMatching = resolver
                .resolveTransFile(DocNameWithExt.from(
                        "doc/marketing.odt"), new LocaleMapping("ja"), Optional.<String>absent());
        assertThat(noMatching.toString()).isEqualTo("./ja/doc/marketing.odt");
    }

}
