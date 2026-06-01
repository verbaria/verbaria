package org.zanata.adapter.properties;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import org.fedorahosted.openprops.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.dto.TranslatedDoc;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import com.google.common.annotations.VisibleForTesting;

public class PropWriter {
    private static final Logger log = LoggerFactory.getLogger(PropWriter.class);

    public static enum CHARSET {
        UTF8(StandardCharsets.UTF_8),
        Latin1(StandardCharsets.ISO_8859_1);

        private final Charset alias;

        CHARSET(Charset alias) {
            this.alias = alias;
        }

        public Charset getAlias() {
            return alias;
        }
    }

    /**
     * Writes a properties file representation of the given {@link Resource} to
     * the given directory in {@link CHARSET#UTF8} or {@link CHARSET#Latin1} encoding.
     *
     * @param doc
     * @param baseDir
     * @param charset {@link CHARSET}
     * @throws IOException
     */
    public static void writeSource(final Resource doc, final Path baseDir,
        final CHARSET charset) throws IOException {
        Path baseFile = baseDir.resolve(doc.getName() + ".properties");
        makeParentDirs(baseFile);

        log.debug("Creating base file {}", baseFile);
        Properties props = new Properties();
        for (TextFlow textFlow : doc.getTextFlows()) {
            List<String> contents = textFlow.getContents();
            if (contents.size() != 1) {
                throw new RuntimeException(
                    "file format does not support plural forms: resId="
                        + textFlow.getId());
            }
            String key = propertyKey(textFlow);
            props.setProperty(key, textFlow.getContents().get(0));
            SimpleComment simpleComment =
                textFlow.getExtensions(true)
                    .findByType(SimpleComment.class);
            if (simpleComment != null && simpleComment.getValue() != null)
                props.setComment(key, simpleComment.getValue());
        }
        // props.store(System.out, null);
        storeProps(props, baseFile, charset);
    }

    /**
     * Writes to given properties file of the given TranslationsResource
     * in {@link CHARSET#UTF8} or {@link CHARSET#Latin1} encoding.
     */
    public static void writeTranslationsFile(final TranslatedDoc translatedDoc,
            final Path propertiesFile, final CHARSET charset,
            boolean createSkeletons, boolean approvedOnly) throws IOException {

        Properties targetProp = new Properties();

        Resource srcDoc = translatedDoc.getSource();
        TranslationsResource doc = translatedDoc.getTranslation();
        if (srcDoc == null) {
            for (TextFlowTarget target : doc.getTextFlowTargets()) {
                textFlowTargetToProperty(target.getResId(), target, targetProp,
                    createSkeletons, approvedOnly);
            }
        } else {
            Map<String, TextFlowTarget> targets = new HashMap<>();
            if (doc != null) {
                for (TextFlowTarget target : doc.getTextFlowTargets()) {
                    targets.put(target.getResId(), target);
                }
            }
            for (TextFlow textFlow : srcDoc.getTextFlows()) {
                TextFlowTarget target = targets.get(textFlow.getId());
                textFlowTargetToProperty(propertyKey(textFlow), target, targetProp,
                    createSkeletons, approvedOnly);
            }
        }
        storeProps(targetProp, propertiesFile, charset);
    }

    /**
     * Writes to given properties file of the given TranslationsResource
     * in {@link CHARSET#UTF8} or {@link CHARSET#Latin1} encoding.
     */
    @VisibleForTesting
    static void writeTranslations(final TranslatedDoc translatedDoc,
            final Path baseDir,
            String bundleName, String locale, final CHARSET charset,
            boolean createSkeletons, boolean approvedOnly) throws IOException {
        Path langFile =
            baseDir.resolve(bundleName + "_" + locale + ".properties");
        makeParentDirs(langFile);
        log.debug("Creating target file {}", langFile);

        writeTranslationsFile(translatedDoc, langFile, charset, createSkeletons, approvedOnly);
    }

    private static void makeParentDirs(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private static void storeProps(Properties props, Path file, CHARSET charset)
            throws IOException {
        try (OutputStream out = new BufferedOutputStream(
                Files.newOutputStream(file))) {
            if (charset.alias.equals(StandardCharsets.UTF_8)) {
                Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8.displayName());
                props.store(writer, null);
            } else {
                props.store(out, null);
            }
        }
    }

    private static boolean hasATranslation(@Nullable TextFlowTarget target) {
        return target != null && !target.getContents().isEmpty();
    }

    private static boolean usable(@Nullable TextFlowTarget target, boolean approvedOnly) {
        return hasATranslation(target)
                && (target.getState().isApproved() ||
                (!approvedOnly && target.getState().isTranslated()));
    }

    private static void textFlowTargetToProperty(String resId,
            TextFlowTarget target, Properties targetProp,
            boolean createSkeletons, boolean approvedOnly) {
        if (!usable(target, approvedOnly)) {
            // don't save fuzzy or empty values
            if (createSkeletons) {
                targetProp.setProperty(resId, "");
            }
            return;
        }
        List<String> contents = target.getContents();
        if (contents.size() != 1) {
            throw new RuntimeException(
                    "file format does not support plural forms: resId=" + resId);
        }
        targetProp.setProperty(resId, contents.get(0));
        SimpleComment simpleComment =
                target.getExtensions(true).findByType(SimpleComment.class);
        if (simpleComment != null && simpleComment.getValue() != null) {
            targetProp.setComment(resId, simpleComment.getValue());
        }
    }

    /**
     * The property key to write. The server hashes long/duplicate property keys
     * into the resId and keeps the original key in PotEntryHeader.context; use
     * that human key when present so pulled files keep readable keys instead of
     * the 32-char hash. Falls back to the textflow id for standard properties.
     */
    private static String propertyKey(TextFlow textFlow) {
        PotEntryHeader peh =
                textFlow.getExtensions(true).findByType(PotEntryHeader.class);
        if (peh != null && peh.getContext() != null
                && !peh.getContext().isEmpty()) {
            return peh.getContext();
        }
        return textFlow.getId();
    }
}
