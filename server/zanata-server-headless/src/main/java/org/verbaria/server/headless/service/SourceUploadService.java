package org.verbaria.server.headless.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.InputSource;
import org.zanata.adapter.po.PoReader2;
import org.zanata.adapter.properties.PropReader;
import org.zanata.adapter.properties.PropWriter;
import org.zanata.adapter.xliff.XliffReader;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.adapter.chrome.ChromeReader;
import org.zanata.adapter.consulo.ConsuloReader;
import org.zanata.util.HashUtil;

/**
 * Parses an uploaded source file (.properties / .po / .pot / .xlf / .xliff)
 * into a {@link Resource} and hands it to {@link DocumentImportService}.
 * Lets the UI add documents without going through the CLI.
 */
@Service
public class SourceUploadService {

    private final DocumentImportService documentImportService;

    public SourceUploadService(DocumentImportService documentImportService) {
        this.documentImportService = documentImportService;
    }

    @Transactional
    public DocumentImportService.ImportResult upload(String projectSlug,
                                                     String versionSlug,
                                                     String fileName,
                                                     InputStream in,
                                                     HAccount actor) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Missing file name");
        }
        String lower = fileName.toLowerCase();
        String baseDocId = stripExtension(fileName);
        Resource resource;
        if (lower.endsWith(".properties")) {
            resource = new Resource(baseDocId);
            resource.setLang(LocaleId.EN_US);
            // Default to UTF-8; admins targeting strict Latin-1 .properties
            // can re-push via the CLI which preserves the legacy charset.
            PropReader reader = new PropReader(PropWriter.CHARSET.UTF8,
                    LocaleId.EN_US, org.zanata.common.ContentState.New);
            reader.extractTemplate(resource, in);
            // Legacy PropReader assigns the raw property key as the TextFlow
            // id — keys >255 chars overflow the resId column and identical
            // hashes never collide. Mirror the PoReader2 behavior: hash the
            // key into a stable 32-char resId and keep the original key in
            // PotEntryHeader.context so the UI's "Message Context" field
            // still surfaces it for translators.
            hashTextFlowIds(resource);
        } else if (lower.endsWith(".po") || lower.endsWith(".pot")) {
            PoReader2 reader = new PoReader2();
            InputSource src = new InputSource(in);
            src.setSystemId(fileName);
            resource = reader.extractTemplate(src, LocaleId.EN_US, baseDocId);
        } else if (lower.endsWith(".xlf") || lower.endsWith(".xliff")) {
            // XliffReader requires a File handle; spool to a temp.
            File tmp = File.createTempFile("zanata-upload-", "-" + fileName);
            try {
                Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                XliffReader reader = new XliffReader();
                resource = reader.extractTemplate(tmp.toPath(), LocaleId.EN_US, baseDocId, "CONTENT");
            } finally {
                tmp.delete();
            }
        } else if (lower.endsWith(".yaml") || lower.endsWith(".yml")) {
            // Consulo-style localization YAML — each top-level key is a
            // TextFlow whose source content is its `text:` field. Filename
            // stems (e.g. consulo.ui.ex.UILocalize) are globally unique
            // across the Consulo platform + every plugin, so we can use
            // them as docIds without prefixing.
            resource = new ConsuloReader().extractTemplate(baseDocId, in);
            hashTextFlowIds(resource);
        } else if (lower.endsWith("messages.json")) {
            resource = new ChromeReader().extractTemplate(baseDocId, in);
            hashTextFlowIds(resource);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file extension: " + fileName
                            + " (accepted: .properties, .po, .pot, .xlf, .xliff, .yaml, .yml, messages.json)");
        }
        return documentImportService.importSource(projectSlug, versionSlug,
                baseDocId, resource, actor);
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /**
     * Replace each TextFlow id with {@link HashUtil#generateHash} of the
     * original id, and stash the original id in a {@link PotEntryHeader}
     * extension's {@code context} so it stays visible in the translate
     * editor's "Message Context" / Details panel.
     */
    private static void hashTextFlowIds(Resource resource) {
        for (TextFlow tf : resource.getTextFlows()) {
            String originalKey = tf.getId();
            if (originalKey == null || originalKey.isEmpty()) {
                continue;
            }
            // Keys are case-insensitive — normalize to lowercase before
            // hashing so "Button.OK" and "button.ok" map to the same resId.
            String hashed = HashUtil.generateHash(
                    originalKey.toLowerCase(java.util.Locale.ROOT));
            tf.setId(hashed);
            // Preserve the original key for display. If the PoReader2 stored
            // a header here already (it never does for .properties), keep it.
            PotEntryHeader existing = tf.getExtensions(true).findByType(PotEntryHeader.class);
            if (existing == null) {
                PotEntryHeader header = new PotEntryHeader();
                header.setContext(originalKey);
                tf.getExtensions(true).add(header);
            } else if (existing.getContext() == null || existing.getContext().isEmpty()) {
                existing.setContext(originalKey);
            }
        }
    }
}
