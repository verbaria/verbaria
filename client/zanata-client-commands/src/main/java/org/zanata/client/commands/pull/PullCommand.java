package org.zanata.client.commands.pull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.PushPullCommand;
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
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.nio.file.Paths;
import java.nio.file.Path;

/**
 * @author Sean Flanigan <a
 *         href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 *
 */
public class PullCommand extends PushPullCommand<PullOptions> {
    private static final Logger log = LoggerFactory
            .getLogger(PullCommand.class);

    private static final Map<String, Class<? extends PullStrategy>> strategies =
            new HashMap<String, Class<? extends PullStrategy>>();

    /**
     * Sync state recorded during {@link #run()} and written to
     * {@code verbaria-lock.json} at the end. Null when not pulling translations.
     */
    private VerbariaLock lock;

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
        java.util.LinkedHashSet<String> resolved = new java.util.LinkedHashSet<>();
        org.zanata.rest.dto.Project[] all =
                getClientFactory().getProjectsClient().getProjects();
        String regex = globToRegex(pattern);
        for (org.zanata.rest.dto.Project p : all) {
            if (p.getId() != null && p.getId().matches(regex)) {
                resolved.add(p.getId());
            }
        }
        log.info("Multi-project pull: {} projects matched", resolved.size());
        String originalProj = getOpts().getProj();
        try {
            int ok = 0, fail = 0;
            for (String slug : resolved) {
                log.info("--- pulling project: {} ---", slug);
                getOpts().setProj(slug);
                try {
                    org.zanata.client.config.LocaleList ll =
                            org.zanata.client.commands.OptionsUtil.fetchLocalesFromServer(
                                    getOpts(), getClientFactory());
                    getOpts().setLocaleMapList(ll);
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
            getOpts().setProj(originalProj);
        }
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

    /**
     * @param logger
     * @param opts
     */
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
        String proj = getOpts().getProj();
        if (proj != null && (proj.contains("*") || proj.contains("?"))) {
            runForProjectGlob(proj);
            return;
        }
        logOptions();

        LocaleList locales = getOpts().getLocaleMapList();
        if (locales == null && (getOpts().getPullType() != PushPullType.Source)) {
            throw new ConfigException("no locales specified");
        }
        PullStrategy strat = createStrategy(getOpts());

        if (strat.isTransOnly()
                && getOpts().getPullType() == PushPullType.Source) {
            log.error("You are trying to pull source only, but source is not available for this project type.\n");
            log.info("Nothing to do. Aborting.\n");
            return;
        }

        if (getOpts().getApprovedOnly()) {
            if (getOpts().getIncludeFuzzy() || getOpts().getCreateSkeletons()) {
                String msg =
                        "You can't use the option --approved together with --create-skeletons or --include-fuzzy";
                log.error(msg);
                throw new ConfigException(msg);
            }
        }

        List<String> unsortedDocNamesForModule =
                getQualifiedDocNamesForCurrentModuleFromServer();
        SortedSet<String> docNamesForModule =
                new TreeSet<String>(unsortedDocNamesForModule);

        SortedSet<String> docsToPull = docNamesForModule;
        if (getOpts().getFromDoc() != null) {
            if (getOpts().getEnableModules()) {
                if (belongsToCurrentModule(getOpts().getFromDoc())) {
                    docsToPull =
                            getDocsAfterFromDoc(getOpts().getFromDoc(),
                                    docsToPull);
                }
                // else fromDoc does not apply to this module
            } else {
                docsToPull =
                        getDocsAfterFromDoc(getOpts().getFromDoc(), docsToPull);
            }
        }

        // TODO compare docNamesForModule with localDocNames, offer to delete
        // obsolete translations from filesystem
        if (docNamesForModule.isEmpty()) {
            log.info("No documents in remote module: {}; nothing to do",
                    getOpts().getCurrentModule());
            return;
        }
        log.info("Pulling {} of {} docs for this module from the server",
                docsToPull.size(), docNamesForModule.size());
        log.debug("Doc names: {}", docsToPull);

        PushPullType pullType = getOpts().getPullType();
        boolean pullSrc =
                pullType == PushPullType.Both
                        || pullType == PushPullType.Source;
        boolean pullTarget =
                pullType == PushPullType.Both || pullType == PushPullType.Trans;

        if (pullSrc && strat.isTransOnly()) {
            log.warn("Source is not available for this project type. Source will not be pulled.\n");
            pullSrc = false;
        }

        if (needToGetStatistics(pullTarget)) {
            log.info("Setting minimum document completion percentage may potentially increase the processing time.");
        }

        if (pullSrc) {
            log.warn("Pull Type set to '{}': existing source-language files may be overwritten/deleted", pullType);
            confirmWithUser("This will overwrite/delete any existing documents and translations in the above directories.\n");
        } else {
            confirmWithUser(
                    "This will overwrite/delete any existing translations in the above directory.\n");
        }

        Optional<Map<String, Map<LocaleId, TranslatedPercent>>> optionalStats =
                prepareStatsIfApplicable(pullTarget, locales);

        // Start a fresh lock describing this sync; populated below per doc.
        lock = new VerbariaLock();
        lock.setServer(getOpts().getUrl() == null ? null
                : getOpts().getUrl().toString());
        lock.setProject(getOpts().getProj());
        lock.setProjectVersion(getOpts().getProjectVersion());
        lock.setGeneratedAt(java.time.Instant.now().toString());

        // The source/base locale (the shared-keys locale, e.g. en-US) is not a
        // translation: when it appears in the locale list, "pulling" it means
        // syncing the on-disk source files from the server (see below).
        int targetLocalesPulled = 0;
        int sourceLocaleSynced = 0;
        for (String qualifiedDocName : docsToPull) {
            try {
                Resource doc = null;
                String localDocName = unqualifiedDocName(qualifiedDocName);
                boolean createSkeletons = getOpts().getCreateSkeletons();
                if (strat.needsDocToWriteTrans() || pullSrc || createSkeletons) {
                    doc = sourceDocResourceClient.getResource(qualifiedDocName,
                            strat.getExtensions());
                    doc.setName(localDocName);
                }
                if (pullSrc) {
                    writeSrcDoc(strat, doc);
                }
                if (doc != null) {
                    // Record the source handle: server revision plus a content
                    // signature, so a source edit shows up in the changelog even
                    // when the server-side revision didn't move.
                    SourceLock src = new SourceLock(doc.getRevision());
                    src.setSig(LockSignature.sourceSignature(doc));
                    lock.document(localDocName).setSource(src);
                    if (pullTarget) {
                        recordSourceLocaleLock(strat, localDocName,
                                qualifiedDocName, doc.getLang());
                    }
                }

                if (pullTarget) {
                    List<LocaleId> skippedLocales = Lists.newArrayList();
                    // The document's own source language is the base/shared-keys
                    // locale, not a translation. When it's in the locale list,
                    // "pulling" it means syncing the on-disk source files from
                    // the server (overriding the scanned originals in place via
                    // writeSrcFile) — NOT writing a translation into the source
                    // dir. Skip the in-loop source write if pullSrc already did
                    // it this pass (pull-type source/both).
                    LocaleId sourceLang = doc == null ? null : doc.getLang();
                    for (LocaleMapping locMapping : locales) {
                        LocaleId locale = new LocaleId(locMapping.getLocale());
                        if (sourceLang != null && sourceLang.equals(locale)) {
                            if (!pullSrc && doc != null) {
                                writeSrcDoc(strat, doc);
                                sourceLocaleSynced++;
                            }
                            continue;
                        }
                        Path transFile =
                                strat.getTransFileToWrite(localDocName,
                                        locMapping);

                        if (shouldPullThisLocale(optionalStats, localDocName, locale)) {
                            pullDocForLocale(strat, doc, localDocName, qualifiedDocName,
                                    createSkeletons, locMapping, transFile);
                            targetLocalesPulled++;
                        } else {
                            skippedLocales.add(locale);
                        }

                    }
                    if (!skippedLocales.isEmpty()) {
                        log.info(
                                "Translation file for document {} for locales {} are skipped due to insufficient completed percentage",
                                localDocName, skippedLocales);
                    }
                }

            } catch (RuntimeException e) {
                String message =
                        "Operation failed: " + e.getMessage() + "\n\n"
                                + "    To retry from the last document, please set the following option(s):\n\n"
                                + "        ";
                if (getOpts().getEnableModules()) {
                    message +=
                            "--resume-from " + getOpts().getCurrentModule(true)
                                    + " ";
                }
                // Note: '.' is included after trailing newlines to prevent them
                // being stripped,
                // since stripping newlines can cause extra text to be appended
                // to the options.
                message +=
                        getOpts().buildFromDocArgument(qualifiedDocName)
                                + "\n\n.";
                log.error(message);
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        if (pullTarget && targetLocalesPulled == 0 && sourceLocaleSynced == 0) {
            log.warn("Nothing was pulled. The locales to pull were {}. Configure "
                    + "target locales for project '{}' (and have translations in "
                    + "them), or set \"targetLocales\" in verbaria.json. To sync "
                    + "the source/base-locale files, use --pull-type source or "
                    + "both.", locales, getOpts().getProj());
        }
        if (sourceLocaleSynced > 0) {
            log.info("Synced source files for {} document(s) from the source "
                    + "(base) locale.", sourceLocaleSynced);
        }

        writeLock();
    }

    /**
     * Writes {@code verbaria-lock.json} at the project root (the cache dir),
     * capturing the sync state for incremental pulls and commit-message
     * generation. Best-effort: a failure here must not fail the pull.
     */
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
            recordTranslationLock(localDocName, sourceLocale, resp.getBody(),
                    null);
        } catch (HttpClientErrorException e) {
            // No base-locale target/locale available; nothing to record.
            log.debug("No base-locale ({}) target for {}: {}", sourceLocale,
                    localDocName, e.getStatusCode());
        }
    }

    /**
     * Records the lock entry for one document+locale from the pulled targets.
     * No-op when there are no countable targets. Uses the include-fuzzy policy
     * to decide which targets count (accepted-only by default).
     */
    private void recordTranslationLock(String localDocName, LocaleId locale,
            TranslationsResource targetDoc, Path transFile) {
        if (lock == null || targetDoc == null) {
            return;
        }
        TranslationLock entry = LockSignature.fromTargets(
                targetDoc.getTextFlowTargets(), getOpts().getIncludeFuzzy());
        if (entry != null) {
            lock.document(localDocName).getTranslations()
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

    /**
     * Returns a list with all documents before fromDoc removed.
     *
     * @param fromDoc
     * @param docNames
     * @return a set with only the documents after fromDoc, inclusive
     * @throws RuntimeException
     *             if no document with the specified name exists
     */
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

    /**
     * source Resource may be null if needsDocToWriteTrans() returns false
     */
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
