package org.zanata.client.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.SubnodeConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.config.ConfigUtil;
import org.zanata.client.config.FileMappingRule;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.config.ZanataConfig;
import org.zanata.client.exceptions.ConfigException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Question;
import static org.zanata.client.commands.ConsoleInteractor.DisplayMode.Warning;
import static org.zanata.client.commands.FileMappingRuleHandler.*;
import static org.zanata.client.commands.FileMappingRuleHandler.Placeholders.allHolders;
import static org.zanata.client.commands.Messages.get;
import java.io.InputStream;

public class OptionsUtil {
    private static final Logger log = LoggerFactory
            .getLogger(OptionsUtil.class);

    /**
     * Loads the config files (controlled by the property userConfig) to supply
     * any values which haven't already been set.
     *
     * @throws Exception
     */
    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper();

    public static void applyConfigFiles(ConfigurableOptions opts)
            throws ConfigurationException, IOException {
        Optional<ZanataConfig> zanataConfig = Optional.empty();
        if (opts instanceof ConfigurableProjectOptions) {
            ConfigurableProjectOptions projOpts =
                    (ConfigurableProjectOptions) opts;
            zanataConfig = applyProjectConfigToProjectOptions(projOpts);
        }
        boolean shouldFetchLocalesFromServer =
                shouldFetchLocalesFromServer(zanataConfig, opts);
        if (opts.getUserConfig() != null) {
            if (opts.getUserConfig().exists()) {
                log.info("Loading user config from {}", opts.getUserConfig());
                INIConfiguration dataConfig =
                        loadIniConfig(opts.getUserConfig());
                applyUserConfig(opts, dataConfig);
            } else {
                System.err.printf(
                        "User config file '%s' not found; ignoring.%n",
                        opts.getUserConfig());
            }
        }
        // we have to wait until user config has been applied
        if (shouldFetchLocalesFromServer) {
            ConfigurableProjectOptions projectOptions =
                    (ConfigurableProjectOptions) opts;
            boolean isProjectGlob = projectOptions.getProj() != null
                    && (projectOptions.getProj().contains("*")
                        || projectOptions.getProj().contains("?"));
            if (!isProjectGlob) {
                LocaleList localeMappings =
                        fetchLocalesFromServer(projectOptions);
                projectOptions.setLocaleMapList(localeMappings);
            }
        }
    }

    public static Optional<ZanataConfig> applyProjectConfigToProjectOptions(ConfigurableProjectOptions opts)
            throws IOException {
        Optional<ZanataConfig> projectConfig =
                readProjectConfigFile(opts);
        if (projectConfig.isPresent()) {
            // local project config is supposed to override user's
            // verbaria.ini,
            // so we apply it first
            applyProjectConfig(opts, projectConfig.get());
        } else if (opts.getProjectConfig() != null) {
            log.warn("Project config file '{}' not found; ignoring.",
                    opts.getProjectConfig());
        }
        return projectConfig;
    }

    public static boolean shouldFetchLocalesFromServer(
            Optional<ZanataConfig> projectConfig, ConfigurableOptions opts) {
        if (!projectConfig.isPresent()) {
            if (opts instanceof ConfigurableProjectOptions) {
                ConfigurableProjectOptions projectOptions =
                        (ConfigurableProjectOptions) opts;
                return StringUtils.isNotEmpty(projectOptions.getProj()) &&
                        StringUtils
                                .isNotEmpty(projectOptions.getProjectVersion());
            }
            return false;
        }
        ZanataConfig zanataConfig = projectConfig.get();
        // Explicit targetLocales is the intended way to pin locales: use them
        // verbatim, don't query the server, and don't nag (unlike the legacy
        // "locales" mapping list below).
        if (zanataConfig.getTargetLocalesAsList() != null) {
            return false;
        }
        boolean localesDefinedInFile =
                zanataConfig.getLocales() != null
                        && !zanataConfig.getLocales().isEmpty();
        if (localesDefinedInFile) {
            ConsoleInteractor console = new ConsoleInteractorImpl(opts);
            console.printfln(Warning, get(
                    "locales.in.config.deprecated"));
            return false;
        } else {
            return true;
        }
    }

    /**
     * Load project config (verbaria.json) file.
     *
     * @param projOpts
     *         project options
     * @return optional ZanataConfig object
     * @throws IOException
     */
    public static Optional<ZanataConfig> readProjectConfigFile(
            ConfigurableProjectOptions projOpts) throws IOException {
        if (projOpts.getProjectConfig() != null) {
            Path projectConfigFile = projOpts.getProjectConfig();
            if (Files.exists(projectConfigFile)) {
                log.info("Loading project config from {}",
                        projectConfigFile);
                try (InputStream in =
                        Files.newInputStream(projectConfigFile)) {
                    return Optional.of(
                            CONFIG_MAPPER.readValue(in, ZanataConfig.class));
                }
            }
        }
        return Optional.empty();

    }


    public static LocaleList fetchLocalesFromServer(
            ConfigurableProjectOptions projectOpts) {
        return ServerRestClient.fetchLocales(projectOpts.getUrl(),
                projectOpts.getUsername(), projectOpts.getKey(),
                projectOpts.getProj(), projectOpts.getProjectVersion());
    }

    /**
     * Applies values from the project configuration unless they have been set
     * directly via parameters.
     *
     * @param config
     */
    private static void applyProjectConfig(ConfigurableProjectOptions opts,
            ZanataConfig config) {
        applyBasicConfig(opts, config);
        if (opts.getProj() == null) {
            opts.setProj(config.getProject());
        }
        if (opts.getProjectVersion() == null) {
            opts.setProjectVersion(config.getProjectVersion());
        }
        if (opts.getProjectType() == null) {
            opts.setProjectType(config.getProjectType());
        }
        applySrcDirAndTransDirFromProjectConfig(opts, config);
        applyIncludesAndExcludesFromProjectConfig(opts, config);
        // Prefer the simple "targetLocales" string when present; it pins the
        // locales to push/pull without querying the server. Otherwise fall back
        // to the legacy "locales" mapping list.
        LocaleList targetLocales = config.getTargetLocalesAsList();
        opts.setLocaleMapList(
                targetLocales != null ? targetLocales : config.getLocales());

        if (opts.getCommandHooks().isEmpty() && config.getHooks() != null) {
            opts.setCommandHooks(config.getHooks());
        }
        opts.setFileMappingRules(config.getRules());
        checkPotentialMistakesInRules(opts, new ConsoleInteractorImpl(opts));
    }

    private static void applyBasicConfig(ConfigurableOptions opts,
            ZanataConfig config) {
        if (opts.getUrl() == null) {
            opts.setUrl(config.getUrl());
        }
    }

    /**
     * Will check potential mistakes in file mapping rules. Missing locale in
     * the rule is considered invalid. Extra "{" and/or "}" will incur warnings
     * (user may have mis-spelt the placeholder)
     *
     * @param opts
     * @param console
     */
    @VisibleForTesting
    protected static void checkPotentialMistakesInRules(
            ConfigurableProjectOptions opts, ConsoleInteractor console) {
        boolean potentialProblem = false;
        boolean invalid = false;
        for (FileMappingRule mappingRule : opts.getFileMappingRules()) {
            String rule = mappingRule.getRule();
            if (!isRuleValid(rule)) {
                console.printfln(Warning, get("invalid.rule"), rule);
                invalid = true;
            }
            if (ruleMayHaveProblem(rule)) {
                console.printfln(Warning, get("unrecognized.variables"),
                        allHolders(), rule);
                potentialProblem = true;
            }
        }
        Preconditions.checkState(!invalid);
        if (potentialProblem && opts.isInteractiveMode()) {
            console.printfln(Question, get("confirm.rule"));
            console.expectYes();
        }
    }

    private static boolean ruleMayHaveProblem(String rule) {
        String remains = stripValidHolders(rule);
        return remains.contains("{") || remains.contains("}");

    }


    /**
     * Note: command line options take precedence over pom.xml which
     * takes precedence over verbaria.json.
     *
     * @see org.zanata.client.commands.OptionMismatchChecker
     * @param opts
     *            options
     * @param config
     *            config from project configuration file i.e. verbaria.json
     */
    @VisibleForTesting
    protected static void applySrcDirAndTransDirFromProjectConfig(
            ConfigurableProjectOptions opts, ZanataConfig config) {
        // apply srcDir configuration
        OptionMismatchChecker<Path> srcDirChecker =
                OptionMismatchChecker.from(opts.getSrcDir(),
                        config.getSrcDirAsPath(),
                        "Source directory");

        if (srcDirChecker.hasValueInConfigOnly()) {
            opts.setSrcDir(config.getSrcDirAsPath());
        }
        srcDirChecker.logHintIfNotDefinedInConfig(
                String.format("\"srcDir\": \"%s\"", opts.getSrcDir()));
        srcDirChecker.logWarningIfValuesMismatch();

        // apply transDir configuration
        OptionMismatchChecker<Path> transDirChecker = OptionMismatchChecker
                .from(opts.getTransDir(), config.getTransDirAsPath(),
                        "Translation directory");
        if (transDirChecker.hasValueInConfigOnly()) {
            opts.setTransDir(config.getTransDirAsPath());
        }
        transDirChecker.logHintIfNotDefinedInConfig(String.format(
                "\"transDir\": \"%s\"", opts.getTransDir()));
        transDirChecker.logWarningIfValuesMismatch();
    }

    /**
     * Note: command line options take precedence over pom.xml which
     * takes precedence over verbaria.json.
     *
     * @see org.zanata.client.commands.OptionMismatchChecker
     * @param opts
     *            options
     * @param config
     *            config from project configuration file i.e. verbaria.json
     */
    protected static void applyIncludesAndExcludesFromProjectConfig(
            ConfigurableProjectOptions opts, ZanataConfig config) {
        OptionMismatchChecker<ImmutableList<String>> includesChecker =
                OptionMismatchChecker
                        .from(opts.getIncludes(), config.getIncludesAsList(),
                                "Includes");
        if (includesChecker.hasValueInConfigOnly()) {
            opts.setIncludes(config.getIncludes());
        }
        Joiner commaJoiner = Joiner.on(",");
        includesChecker.logHintIfNotDefinedInConfig(String.format(
                "\"includes\": \"%s\"",
                commaJoiner.join(opts.getIncludes())));
        includesChecker.logWarningIfValuesMismatch();

        OptionMismatchChecker<ImmutableList<String>> excludesChecker =
                OptionMismatchChecker
                        .from(opts.getExcludes(), config.getExcludesAsList(),
                                "Excludes");
        if (excludesChecker.hasValueInConfigOnly()) {
            opts.setExcludes(config.getExcludes());
        }
        excludesChecker
                .logHintIfNotDefinedInConfig(
                        String.format("\"excludes\": \"%s\"",
                                commaJoiner.join(opts.getExcludes())));
        excludesChecker.logWarningIfValuesMismatch();
    }

    /**
     * Applies values from the user's personal configuration unless they have
     * been set directly (by parameters or by project configuration).
     *
     * @param config
     */
    /**
     * Loads an INI file using commons-configuration2's builder API.
     */
    public static INIConfiguration loadIniConfig(File iniFile)
            throws ConfigurationException {
        return new FileBasedConfigurationBuilder<>(INIConfiguration.class)
                .configure(new Parameters().fileBased().setFile(iniFile))
                .getConfiguration();
    }

    public static void applyUserConfig(ConfigurableOptions opts,
            INIConfiguration config) {
        if (!opts.isDebugSet()) {
            Boolean debug = config.getBoolean("defaults.debug", null);
            if (debug != null)
                opts.setDebug(debug);
        }

        if (!opts.isErrorsSet()) {
            Boolean errors = config.getBoolean("defaults.errors", null);
            if (errors != null)
                opts.setErrors(errors);
        }

        if (!opts.isQuietSet()) {
            Boolean quiet = config.getBoolean("defaults.quiet", null);
            if (quiet != null)
                opts.setQuiet(quiet);
        }

        if (!opts.isInteractiveModeSet()) {
            Boolean batchMode = config.getBoolean("defaults.batchMode", null);
            if (batchMode != null)
                opts.setInteractiveMode(!batchMode);
        }
        if ((opts.getUsername() == null || opts.getKey() == null)
                && opts.getUrl() != null) {
            SubnodeConfiguration servers = config.getSection("servers");
            if (servers != null) {
                String prefix = ConfigUtil.findPrefix(servers, opts.getUrl());
                if (prefix != null) {
                    if (opts.getUsername() == null) {
                        opts.setUsername(servers.getString(prefix + ".username",
                                null));
                    }
                    if (opts.getKey() == null) {
                        opts.setKey(servers.getString(prefix + ".key", null));
                    }
                }
            }
        }
    }

    private static void checkMandatoryOptsForRequestFactory(
            ConfigurableOptions opts) {
        if (opts.getUrl() == null) {
            throw new ConfigException("Server URL must be specified");
        }
        if (opts.isAuthRequired() && opts.getUsername() == null) {
            throw new ConfigException("Username must be specified");
        }
        if (opts.isAuthRequired() && opts.getKey() == null) {
            throw new ConfigException("API key must be specified");
        }
        if (opts.isDisableSSLCert()) {
            log.warn("SSL certificate verification will be disabled. You should consider adding the certificate instead of disabling it.");
        }
    }

    public static String stripValidHolders(String rule) {
        String temp = rule;
        for (Placeholders placeholder : Placeholders.values()) {
            temp = temp.replace(placeholder.holder(), "");
        }
        return temp;
    }

}
