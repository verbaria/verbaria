package org.zanata.client.commands.pull;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.client.commands.GenericArchiveTransport;
import org.zanata.client.commands.PushPullCommand;
import org.zanata.client.commands.PushPullType;

public class PullCommand extends PushPullCommand<PullOptions> {
    private static final Logger log = LoggerFactory
            .getLogger(PullCommand.class);

    public PullCommand(PullOptions opts) {
        super(opts);
    }

    @Override
    public void run() throws Exception {
        logOptions(log, getOpts());
        new GenericArchiveTransport().pull(getOpts());
    }

    static void logOptions(Logger logger, PullOptions opts) {
        logger.info("Server: {}", opts.getUrl());
        logger.info("Project: {}", opts.getProj());
        logger.info("Version: {}", opts.getProjectVersion());
        logger.info("Username: {}", opts.getUsername());
        logger.info("Project type: {}", opts.getProjectType());
        logger.info("Locales to pull: {}",
                opts.getLocaleMapList() == null || opts.getLocaleMapList().isEmpty()
                        ? "(all locales configured on the server)"
                        : opts.getLocaleMapList());
        if (opts.getPullType() == PushPullType.Source) {
            logger.info("Pulling source documents only");
            logger.info("Source-language directory (originals): {}",
                    expand(opts.getSrcDir()));
        } else if (opts.getPullType() == PushPullType.Trans) {
            logger.info("Pulling target documents (translations) only");
            logger.info("Target-language base directory (translations): {}",
                    expand(opts.getTransDir()));
        } else {
            logger.info("Pulling source and target (translation) documents");
            logger.info("Source-language directory (originals): {}",
                    expand(opts.getSrcDir()));
            logger.info("Target-language base directory (translations): {}",
                    expand(opts.getTransDir()));
        }
    }

    private static Object expand(Path dir) {
        return dir == null ? null : dir.toAbsolutePath().normalize();
    }
}
