package org.zanata.client.commands.push;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.GenericArchiveTransport;
import org.zanata.client.commands.PushPullCommand;
import org.zanata.client.commands.PushPullType;

public class PushCommand extends PushPullCommand<PushOptions> {
    private static final Logger log = LoggerFactory
            .getLogger(PushCommand.class);

    public PushCommand(PushOptions opts) {
        super(opts);
    }

    @Override
    public void run() throws Exception {
        logOptions(log, getOpts());
        new GenericArchiveTransport().push(getOpts());
    }

    public static void logOptions(Logger logger, PushOptions opts) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        logger.info("Server: {}", opts.getUrl());
        logger.info("Project: {}", opts.getProj());
        logger.info("Version: {}", opts.getProjectVersion());
        logger.info("Username: {}", opts.getUsername());
        logger.info("Project type: {}", opts.getProjectType());
        logger.info("Merge type: {}", opts.getMergeType());
        logger.info("Include patterns: {}",
                StringUtils.join(opts.getIncludes(), " "));
        logger.info("Exclude patterns: {}",
                StringUtils.join(opts.getExcludes(), " "));
        logger.info("Case sensitive: {}", opts.getCaseSensitive());
        logger.info("Default excludes: {}", opts.getDefaultExcludes());
        if (opts.getPushType() == PushPullType.Trans) {
            logger.info("Pushing target documents only");
            logger.info("Locales to push: {}", localesToPush(opts));
        } else if (opts.getPushType() == PushPullType.Source) {
            logger.info("Pushing source documents only");
        } else {
            logger.info("Pushing source and target documents");
            logger.info("Locales to push: {}", localesToPush(opts));
        }
        logger.info("Source directory (originals): {}", expand(opts.getSrcDir()));
        logger.info("Target base directory (translations): {}",
                expand(opts.getTransDir()));
    }

    private static Object localesToPush(PushOptions opts) {
        if (opts.getLocaleMapList() == null || opts.getLocaleMapList().isEmpty()) {
            return "(detected from local directory names)";
        }
        return opts.getLocaleMapList();
    }

    private static Object expand(Path dir) {
        return dir == null ? null : dir.toAbsolutePath().normalize();
    }
}
