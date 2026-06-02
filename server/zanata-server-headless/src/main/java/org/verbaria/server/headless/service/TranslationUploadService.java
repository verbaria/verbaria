package org.verbaria.server.headless.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.InputSource;
import org.zanata.adapter.po.PoReader2;
import org.zanata.adapter.properties.PropReader;
import org.zanata.adapter.properties.PropWriter;
import org.zanata.adapter.xliff.XliffReader;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.adapter.consulo.ConsuloReader;
import org.zanata.util.HashUtil;

/**
 * Parses an uploaded translation file (.properties / .po / .pot / .xlf / .xliff
 * / .yaml / .yml) into a {@link TranslationsResource} and hands it to
 * {@link DocumentImportService#importTranslations}. Mirrors
 * {@link SourceUploadService} for the source side.
 *
 * <p>The {@code docId} parameter ties the upload to an existing source
 * document — translations are matched to text flows by {@code resId} within
 * that document.</p>
 *
 * <p>For .properties and .yaml uploads we re-hash each incoming target's
 * {@code resId} via {@link HashUtil#generateHash} (lowercased) so it matches
 * the resId computed at source-upload time. Without this the join would
 * fail and every row would be skipped.</p>
 */
@Service
public class TranslationUploadService {

    private final DocumentImportService documentImportService;

    public TranslationUploadService(DocumentImportService documentImportService) {
        this.documentImportService = documentImportService;
    }

    @Transactional
    public DocumentImportService.TranslationsImportResult upload(
            String projectSlug, String versionSlug, String docId,
            String fileName, String localeId, InputStream in, HAccount actor)
            throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Missing file name");
        }
        if (localeId == null || localeId.isBlank()) {
            throw new IllegalArgumentException("Missing locale");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        TranslationsResource resource;
        boolean hashIds = false;

        if (lower.endsWith(".properties")) {
            resource = new TranslationsResource();
            PropReader reader = new PropReader(PropWriter.CHARSET.UTF8,
                    new LocaleId(localeId), ContentState.Translated);
            reader.extractTarget(resource, in);
            hashIds = true;
        } else if (lower.endsWith(".po") || lower.endsWith(".pot")) {
            PoReader2 reader = new PoReader2();
            InputSource src = new InputSource(in);
            src.setSystemId(fileName);
            resource = reader.extractTarget(src);
        } else if (lower.endsWith(".xlf") || lower.endsWith(".xliff")) {
            // XliffReader requires a File handle; spool to a temp.
            File tmp = File.createTempFile("zanata-trans-", "-" + fileName);
            try {
                Files.copy(in, tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
                resource = new XliffReader().extractTarget(tmp.toPath());
            } finally {
                tmp.delete();
            }
        } else if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            resource = new ConsuloReader().extractTarget(in);
            hashIds = true;
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file extension: " + fileName
                            + " (accepted: .properties, .po, .pot, .xlf, .xliff, .yaml, .yml)");
        }

        if (hashIds) hashTargetResIds(resource);
        return documentImportService.importTranslations(
                projectSlug, versionSlug, docId, localeId, resource, actor);
    }

    /**
     * Replace each target's {@code resId} with {@link HashUtil#generateHash}
     * of the lowercased original — mirroring the source-upload path so the
     * upload joins to the existing TextFlows.
     */
    private static void hashTargetResIds(TranslationsResource resource) {
        for (TextFlowTarget t : resource.getTextFlowTargets()) {
            String original = t.getResId();
            if (original == null || original.isEmpty()) continue;
            t.setResId(HashUtil.generateHash(original.toLowerCase(Locale.ROOT)));
        }
    }
}
