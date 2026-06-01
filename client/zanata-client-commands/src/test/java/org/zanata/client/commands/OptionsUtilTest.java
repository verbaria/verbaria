package org.zanata.client.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Question;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Warning;
import static org.zanata.client.commands.FileMappingRuleHandler.Placeholders.allHolders;
import static org.zanata.client.commands.Messages.get;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.apache.commons.configuration2.INIConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.zanata.client.InMemoryFs;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.zanata.client.config.FileMappingRule;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.config.ZanataConfig;
import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

public class OptionsUtilTest {
    @RegisterExtension
    public InMemoryFs tempFolder = new InMemoryFs();
    private ConfigurableProjectOptions opts;
    private ZanataConfig config;
    @Mock
    private ConsoleInteractor console;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        opts = new ConfigurableProjectOptionsImpl() {
            @Override
            public ZanataCommand initCommand() {
                return null;
            }

            @Override
            public String getCommandName() {
                return "testCommand";
            }

            @Override
            public String getCommandDescription() {
                return "testing";
            }
        };
        config = new ZanataConfig();
    }

    @Test
    public void willApplyConfigFromFileIfNotSetInOptions() throws Exception {
        // Given: options are not set and exists in zanata config
        config.setSrcDir("a");
        config.setTransDir("b");
        config.setIncludes("*.properties");
        config.setExcludes("a.properties");

        // When:
        OptionsUtil.applySrcDirAndTransDirFromProjectConfig(opts, config);
        OptionsUtil.applyIncludesAndExcludesFromProjectConfig(opts, config);

        // Then:
        assertThat(opts.getSrcDir()).isEqualTo(Paths.get("a"));
        assertThat(opts.getTransDir()).isEqualTo(Paths.get("b"));
        assertThat(opts.getIncludes()).contains("*.properties");
        assertThat(opts.getExcludes()).contains("a.properties");
    }

    @Test
    public void willSetToDefaultValueIfNeitherHasValue() {
        OptionsUtil.applySrcDirAndTransDirFromProjectConfig(opts, config);

        assertThat(opts.getSrcDir()).isEqualTo(Paths.get("."));
        assertThat(opts.getTransDir()).isEqualTo(Paths.get("."));
    }

    @Test
    public void optionTakesPrecedenceOverConfig() {
        // Given: options are set in both places
        opts.setSrcDir(Paths.get("pot"));
        opts.setTransDir(Paths.get("."));
        opts.setIncludes("*.properties");
        opts.setExcludes("a.properties,b.properties");
        config.setSrcDir("a");
        config.setTransDir("b");
        config.setIncludes("b.b");
        config.setExcludes("e,f");

        // When:
        OptionsUtil.applySrcDirAndTransDirFromProjectConfig(opts, config);
        OptionsUtil.applyIncludesAndExcludesFromProjectConfig(opts, config);

        // Then:
        assertThat(opts.getSrcDir()).isEqualTo(Paths.get("pot"));
        assertThat(opts.getTransDir()).isEqualTo(Paths.get("."));
        assertThat(opts.getIncludes()).contains("*.properties");
        assertThat(opts.getExcludes()).contains("a.properties", "b.properties");
    }

    @Test
    public void applyUserConfigTestDefault() throws MalformedURLException {
        opts.setUsername("username");
        opts.setUrl(new URL("http://localhost"));

        INIConfiguration config =
                Mockito.mock(INIConfiguration.class);
        OptionsUtil.applyUserConfig(opts, config);

        verify(config).getSection("servers");
        verify(config).getBoolean("defaults.debug", null);
        verify(config).getBoolean("defaults.errors", null);
        verify(config).getBoolean("defaults.quiet", null);
        verify(config).getBoolean("defaults.batchMode", null);
    }

    @Test
    public void applyUserConfigTest() {
        opts.setDebug(false);
        opts.setErrors(false);
        opts.setQuiet(false);
        opts.setInteractiveMode(false);

        INIConfiguration config =
                Mockito.mock(INIConfiguration.class);
        OptionsUtil.applyUserConfig(opts, config);

        verifyNoMoreInteractions(config);
    }

    @Test
    public void willWarnUserIfRuleSeemsWrong() {
        opts.setInteractiveMode(false);
        String rule = "{foo}/{bar}/{locale}";
        opts.setFileMappingRules(Lists.newArrayList(new FileMappingRule(rule)));
        OptionsUtil.checkPotentialMistakesInRules(opts, console);

        verify(console).printfln(Warning, get("unrecognized.variables"),
                allHolders(), rule);
    }

    @Test
    public void willAskUserToConfirmIfRuleSeemsWrongAndInInteractiveMode() {
        opts.setInteractiveMode(true);
        String rule = "{foo}/{bar}/{locale}";
        opts.setFileMappingRules(Lists.newArrayList(new FileMappingRule(rule)));
        OptionsUtil.checkPotentialMistakesInRules(opts, console);

        verify(console).printfln(Warning, get("unrecognized.variables"),
                allHolders(), rule);
        verify(console).printfln(Question, get("confirm.rule"));
    }

    @Test
    public void willThrowExceptionIfRuleIsInvalid() {
        String rule = "{filename}/{path}";
        opts.setFileMappingRules(Lists.newArrayList(new FileMappingRule(rule)));

        assertThrows(IllegalStateException.class, () ->
                OptionsUtil.checkPotentialMistakesInRules(opts, console));
    }

    @Test
    public void willNotFetchFromServerIfNoProjectConfigDefined() {
        boolean result = OptionsUtil
                .shouldFetchLocalesFromServer(Optional.empty(),
                        opts);
        assertThat(result).isFalse();
    }

    @Test
    public void willFetchFromServerIfProjectConfigHasNoLocalesDefined() {
        boolean result = OptionsUtil.shouldFetchLocalesFromServer(
                Optional.of(new ZanataConfig()),
                opts);
        assertThat(result).isTrue();
    }

    @Test
    public void willNotFetchFromServerIfProjectConfigHasLocalesDefined() {
        ZanataConfig config = new ZanataConfig();
        LocaleList locales = new LocaleList();
        locales.add(new LocaleMapping("zh"));
        config.setLocales(locales);
        opts.setInteractiveMode(false);
        boolean result = OptionsUtil.shouldFetchLocalesFromServer(
                Optional.of(config), opts);
        assertThat(result).isFalse();
    }

    @Test
    public void readProjectConfigWillReturnEmptyIfNoProjectConfigDefinedInOptions()
            throws IOException {
        assertThat(OptionsUtil.readProjectConfigFile(opts).isPresent()).isFalse();
    }

    @Test
    public void readProjectConfigWillReturnEmptyIfProjectConfigDefinedInOptionsDoesNotExist()
            throws IOException {
        opts.setProjectConfig(Paths.get("does not exist"));
        assertThat(OptionsUtil.readProjectConfigFile(opts).isPresent()).isFalse();
    }

    @Test
    public void readProjectConfigCanReadJsonZanataConfig() throws Exception {
        Path configFile = tempFolder.newFile();
        List<String> configLines = Lists.newArrayList(
                "{",
                "  \"url\": \"http://localhost:8080/\",",
                "  \"project\": \"sample-project\",",
                "  \"projectVersion\": \"1.1\"",
                "}");
        Files.write(configFile, configLines, Charsets.UTF_8);
        opts.setProjectConfig(configFile);

        assertThat(OptionsUtil.readProjectConfigFile(opts).isPresent()).isTrue();
        assertThat(OptionsUtil.readProjectConfigFile(opts).get())
                .isInstanceOfAny(ZanataConfig.class);
        ZanataConfig zc = OptionsUtil.readProjectConfigFile(opts).get();
        assertThat(zc.getProject()).isEqualTo("sample-project");
        assertThat(zc.getProjectVersion()).isEqualTo("1.1");
        assertThat(zc.getUrl().toString()).isEqualTo("http://localhost:8080/");
    }
}
