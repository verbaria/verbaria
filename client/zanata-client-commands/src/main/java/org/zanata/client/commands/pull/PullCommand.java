package org.zanata.client.commands.pull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.OptionsUtil;
import org.zanata.client.commands.PushPullCommand;
import org.zanata.client.commands.GenericArchiveTransport;
import org.zanata.client.commands.PushPullType;
import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.dto.LocaleMappedTranslatedDoc;
import org.zanata.client.exceptions.ConfigException;
import org.zanata.client.lock.LockSignature;
import org.zanata.client.lock.VerbariaLock;
import org.zanata.client.lock.VerbariaLock.SourceLock;
import org.zanata.client.lock.VerbariaLock.TranslationLock;
import org.zanata.client.lock.VerbariaLockReaderWriter;
import org.zanata.common.LocaleId;
import org.zanata.rest.client.RestClientFactory;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class PullCommand extends PushPullCommand<PullOptions> {
    private static final Logger log = LoggerFactory
            .getLogger(PullCommand.class);

    private static final Map<String, Class<? extends PullStrategy>> strategies =
            new HashMap<String, Class<? extends PullStrategy>>();

    private VerbariaLock lock;

    private boolean inProjectGlob;
    private String currentGlobSlug;

    static {
        strategies.put(PROJECT_TYPE_UTF8_PROPERTIES,
                UTF8PropertiesStrategy.class);
        strategies.put(PROJECT_TYPE_PROPERTIES, PropertiesStrategy.class);
        strategies.put(PROJECT_TYPE_GETTEXT, GettextPullStrategy.class);
        strategies.put(PROJECT_TYPE_PUBLICAN, GettextDirStrategy.class);
        strategies.put(PROJECT_TYPE_XLIFF, XliffStrategy.class);
        strategies.put(PROJECT_TYPE_XML, XmlStrategy.class);
        strategies.put(PROJECT_TYPE_OFFLINE_PO, OfflinePoStrategy.class);
        strategies.put(PROJECT_TYPE_CONSULO, ConsuloStrategy.class);
        strategies.put(PROJECT_TYPE_CHROME, ChromeStrategy.class);
    }

    public PullCommand(PullOptions opts) {
        super(opts);
    }

    public PullCommand(PullOptions opts, RestClientFactory clientFactory) {
        super(opts, clientFactory);
    }

    public PullStrategy createStrategy(PullOptions opts)
            throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException, InstantiationException {
        Class<? extends PullStrategy> clazz = strategies.get(opts.getProjectType());
        if (clazz == null) {
            throw new RuntimeException("unknown project type: "
                    + opts.getProjectType());
        }
        Constructor<? extends PullStrategy> ctor =
                clazz.getDeclaredConstructor(PullOptions.class);
        assert ctor != null : "strategy must have constructor which accepts PullOptions";
        return ctor.newInstance(getOpts());
    }

    private void runForProjectGlob(String pattern) throws Exception {
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        Project[] all =
                getClientFactory().getProjectsClient().getProjects();
        String regex = globToRegex(pattern);
        for (Project p : all) {
            if (p.getId() != null && p.getId().matches(regex)) {
                resolved.add(p.getId());
            }
        }
        log.info("Multi-project pull: {} projects matched", resolved.size());
        String originalProj = getOpts().getProj();
        lock = new VerbariaLock();
        lock.setServer(getOpts().getUrl() == null ? null
                : getOpts().getUrl().toString());
        lock.setProject(originalProj);
        lock.setProjectVersion(getOpts().getProjectVersion());
        lock.setGeneratedAt(Instant.now().toString());
        LocaleList pinnedLocales = getOpts().getLocaleMapList();
        boolean localesPinned =
                pinnedLocales != null && !pinnedLocales.isEmpty();
        inProjectGlob = true;
        try {
            int ok = 0, fail = 0;
            for (String slug : resolved) {
                log.info("--- pulling project: {} ---", slug);
                getOpts().setProj(slug);
                currentGlobSlug = slug;
                try {
                    if (!localesPinned) {
                        LocaleList ll = OptionsUtil.fetchLocalesFromServer(
                                getOpts(), getClientFactory());
                        getOpts().setLocaleMapList(ll);
                    }
                    rebuildProjectScopedClients();
                    run();
                    ok++;
                } catch (Exception ex) {
                    log.warn("pull failed for project {}: {}", slug, ex.getMessage());
                    fail++;
                }
            }
            log.info("Multi-project pull done: {} ok, {} failed", ok, fail);
        } finally {
            inProjectGlob = false;
            currentGlobSlug = null;
            getOpts().setProj(originalProj);
        }
        writeLock();
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') i++;
            } else if (c == '?') {
                sb.append('.');
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.append('$').toString();
    }

    private void logOptions() {
        logOptions(log, getOpts());
        log.info("Create skeletons for untranslated messages/files: {}",
                getOpts().getCreateSkeletons());
        log.info("Approved translations only: {}", getOpts().getApprovedOnly());
        if (getOpts().getFromDoc() != null) {
            log.info("From document: {}", getOpts().getFromDoc());
        }
        if (getOpts().isDryRun()) {
            log.info("DRY RUN: no permanent changes will be made");
        }
    }

    static void logOptions(Logger logger, PullOptions opts) {
        logger.info("Server: {}", opts.getUrl());
        logger.info("Project: {}", opts.getProj());
        logger.info("Version: {}", opts.getProjectVersion());
        logger.info("Username: {}", opts.getUsername());
        logger.info("Project type: {}", opts.getProjectType());
        logger.info("Enable modules: {}", opts.getEnableModules());
        if (opts.getEnableModules()) {
            logger.info("Current Module: {}", opts.getCurrentModule());
            if (opts.isRootModule()) {
                logger.info("Root module: YES");
                if (logger.isDebugEnabled()) {
                    logger.debug("Modules: {}",
                            StringUtils.join(opts.getAllModules(), ", "));
                }
            }
        }
        logger.info("Locales to pull: {}", opts.getLocaleMapList());
        logger.info("Encode tab as \\t: {}", opts.getEncodeTabs());
        logger.info("Current directory: {}", System.getProperty("user.dir"));
        if (opts.getPullType() == PushPullType.Source) {
            logger.info("Pulling source documents only");
            logger.info("Source-language directory (originals): {}",
                    opts.getSrcDir());
        } else if (opts.getPullType() == PushPullType.Trans) {
            logger.info("Pulling target documents (translations) only");
            logger.info("Target-language base directory (translations): {}",
                    opts.getTransDir());
            logger.info("Minimum accepted translation percentage (message based): {}%",
                    opts.getMinDocPercent());
        } else {
            logger.info("Pulling source and target (translation) documents");
            logger.info("Source-language directory (originals): {}",
                    opts.getSrcDir());
            logger.info("Target-language base directory (translations): {}",
                    opts.getTransDir());
            logger.info("Minimum accepted translation percentage (message based): {}%",
                    opts.getMinDocPercent());
        }
    }

    @SuppressFBWarnings({"SLF4J_FORMAT_SHOULD_BE_CONST"})
    @Override
    public void run() throws Exception {
        logOptions();
        new GenericArchiveTransport().pull(getOpts());
    }

    private String lockDocKey(String localDocName) {
        return inProjectGlob ? currentGlobSlug + "/" + localDocName
                : localDocName;
    }

    private void writeLock() {
        if (lock == null || getOpts().isDryRun()) {
            return;
        }
        try {
            Path cfg = getOpts().getProjectConfig();
            Path lockDir = cfg != null && cfg.getParent() != null
                    ? cfg.getParent() : Paths.get(".");
            Path lockFile =
                    lockDir.resolve(VerbariaLockReaderWriter.FILE_NAME);
            if (VerbariaLockReaderWriter.writeIfChanged(lock, lockFile)) {
                log.info("Wrote sync state to {}", lockFile);
            } else {
                log.info("Sync state unchanged; left {} as-is", lockFile);
            }
        } catch (RuntimeException e) {
            log.warn("Could not write {}: {}",
                    VerbariaLockReaderWriter.FILE_NAME, e.getMessage());
        }
    }

    private void recordSourceLocaleLock(PullStrategy strat, String localDocName,
            String docId, LocaleId sourceLocale) {
        if (lock == null || sourceLocale == null) {
            return;
        }
        try {
            ResponseEntity<TranslationsResource> resp =
                    transDocResourceClient.getTranslations(docId, sourceLocale,
                            strat.getExtensions(), false, null);
            recordTranslationLock(localDocName, sourceLocale,
                    resp == null ? null : resp.getBody(), null);
        } catch (HttpClientErrorException e) {
            log.debug("No base-locale ({}) target for {}: {}", sourceLocale,
                    localDocName, e.getStatusCode());
        }
    }

    private void recordTranslationLock(String localDocName, LocaleId locale,
            TranslationsResource targetDoc, Path transFile) {
        if (lock == null || targetDoc == null) {
            return;
        }
        TranslationLock entry = LockSignature.fromTargets(
                targetDoc.getTextFlowTargets(), getOpts().getIncludeFuzzy());
        if (entry != null) {
            lock.document(lockDocKey(localDocName)).getTranslations()
                    .put(locale.getId(), entry);
        }
    }

    @VisibleForTesting
    protected void pullDocForLocale(PullStrategy strat, Resource doc,
            String localDocName, String docId, boolean createSkeletons,
            LocaleMapping locMapping, Path transFile) throws IOException {
        LocaleId locale = new LocaleId(locMapping.getLocale());

        ResponseEntity<TranslationsResource> transResponse;
        try {
            transResponse = transDocResourceClient.getTranslations(docId,
                    locale, strat.getExtensions(), createSkeletons, null);
        } catch (HttpClientErrorException.NotFound e) {
            if (!createSkeletons) {
                log.info(
                        "No translations found in locale {} for document {}",
                        locale, localDocName);
            } else {
                LocaleMappedTranslatedDoc translatedDoc =
                        new LocaleMappedTranslatedDoc(doc, null, locMapping);
                writeTargetDoc(strat, localDocName, translatedDoc);
            }
            return;
        }

        TranslationsResource targetDoc = transResponse.getBody();
        LocaleMappedTranslatedDoc translatedDoc =
                new LocaleMappedTranslatedDoc(doc, targetDoc, locMapping);
        writeTargetDoc(strat, localDocName, translatedDoc);
        recordTranslationLock(localDocName, locale, targetDoc, transFile);
    }

    private SortedSet<String> getDocsAfterFromDoc(String fromDoc,
            SortedSet<String> docNames) {
        SortedSet<String> docsToPull;
        if (!docNames.contains(fromDoc)) {
            throw new RuntimeException(
                    "Document with id "
                            + fromDoc
                            + " not found, unable to start pull from unknown document. Aborting.");
        }
        docsToPull = docNames.tailSet(fromDoc);
        int numSkippedDocs = docNames.size() - docsToPull.size();
        log.info("Skipping {} document(s) before {}.", numSkippedDocs, fromDoc);
        return docsToPull;
    }

    private void writeSrcDoc(PullStrategy strat, Resource doc)
            throws IOException {
        if (!getOpts().isDryRun()) {
            log.info("Writing source file for document {}", doc.getName());
            strat.writeSrcFile(doc);
        } else {
            log.info(
                    "Writing source file for document {} (skipped due to dry run)",
                    doc.getName());
        }
    }

    private void writeTargetDoc(PullStrategy strat, String localDocName,
            LocaleMappedTranslatedDoc translatedDoc)
            throws IOException {
        LocaleMapping locMapping = translatedDoc.getLocale();
        if (!getOpts().isDryRun()) {
            log.info("Writing translation file in locale {} for document {}",
                    locMapping.getLocalLocale(), localDocName);
            strat.writeTransFile(localDocName, translatedDoc);
        } else {
            log.info(
                    "Writing translation file in locale {} for document {} (skipped due to dry run)",
                    locMapping.getLocalLocale(), localDocName);
        }
    }

}
