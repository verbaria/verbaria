package org.zanata.client.commands.pull;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zanata.client.config.FileMappingRule;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.zanata.client.TestUtils.createAndAddLocaleMapping;

public class XmlStrategyPullTest {
    private XmlStrategy strategy;
    private PullOptionsImpl opts;

    @BeforeEach
    public void setUp() {
        opts = new PullOptionsImpl();
        opts.setLocaleMapList(new LocaleList());
        opts.setProjectType("xml");
        strategy = new XmlStrategy(opts);
    }

    @Test
    public void canGetTransFileWithoutMappingRule() {
        LocaleMapping deMapping = createAndAddLocaleMapping("de",
            Optional.<String>absent(), opts);
        LocaleMapping zhMapping =
            createAndAddLocaleMapping("zh-CN",
                Optional.of("zh-Hans"),
                opts);

        Path deTransFile =
            strategy.getTransFileToWrite("foo/message", deMapping);

        assertThat(deTransFile)
                .isEqualTo(Paths.get("foo/message_de.xml"));

        Path zhTransFile =
            strategy.getTransFileToWrite("foo/message", zhMapping);
        assertThat(zhTransFile).isEqualTo(
            Paths.get("foo/message_zh_Hans.xml"));

    }

    @Test
    public void canGetTransFileWithMappingRule() {
        LocaleMapping deMapping = createAndAddLocaleMapping("de",
            Optional.<String>absent(), opts);
        LocaleMapping zhMapping =
            createAndAddLocaleMapping("zh-CN",
                Optional.of("zh-Hans"),
                opts);
        opts.setFileMappingRules(Lists.newArrayList(
            new FileMappingRule(
                "{path}/{filename}_{locale_with_underscore}.{extension}")));

        Path deTransFile =
            strategy.getTransFileToWrite("foo/message", deMapping);

        assertThat(deTransFile).isEqualTo(Paths.get("foo/message_de.xml"));

        Path zhTransFile =
            strategy.getTransFileToWrite("foo/message", zhMapping);
        assertThat(zhTransFile).isEqualTo(
            Paths.get("foo/message_zh_Hans.xml"));

    }

}
