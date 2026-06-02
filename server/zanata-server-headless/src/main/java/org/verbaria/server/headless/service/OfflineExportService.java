package org.verbaria.server.headless.service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.zanata.adapter.po.PoWriter2;
import org.zanata.adapter.properties.PropWriter;
import org.zanata.adapter.xliff.XliffWriter;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.adapter.consulo.ConsuloWriter;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.common.dto.TranslatedDoc;
import org.zanata.model.HDocument;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Produces offline-translation bundles. The file format inside the ZIP is
 * driven by the project's {@code defaultProjectType} (Gettext/Podir → .po,
 * Properties → .properties Latin-1, Utf8Properties → .properties UTF-8,
 * Xliff → .xlf, Xml/File/null → .po fallback). Also produces a TMX dump
 * for the matching {@code /tm/...} endpoint.
 */
@Service
public class OfflineExportService {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;

    public OfflineExportService(ProjectIterationRepository iterationRepository,
                                DocumentRepository documentRepository,
                                TextFlowRepository textFlowRepository,
                                TextFlowTargetRepository targetRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.targetRepository = targetRepository;
        this.textFlowRepository = textFlowRepository;
    }

    public record Bundle(String filename, String contentType, byte[] bytes) {}

    /**
     * Produce a single translated YAML for one document + locale, suitable
     * for direct download by {@code zanata-cli pull --project-type yaml}.
     * Returns empty Optional if the document doesn't exist.
     */
    @Transactional(readOnly = true)
    public Optional<Bundle> yamlForDoc(String projectSlug, String versionSlug,
                                       String docId, LocaleId locale) throws IOException {
        Optional<HDocument> docOpt = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (docOpt.isEmpty()) return Optional.empty();
        HDocument doc = docOpt.get();
        Resource source = toResource(doc);
        TranslationsResource trans = toTranslations(doc, locale);
        byte[] body = writeYaml(source, trans);
        return Optional.of(new Bundle(docId + ".yaml", "application/x-yaml", body));
    }

    /**
     * Render a single document's translations in the project's configured
     * file format, ready for direct download. Used by the per-row
     * "Download translated" action in the version's documents grid.
     */
    @Transactional(readOnly = true)
    public Optional<Bundle> singleTranslatedDoc(String projectSlug, String versionSlug,
                                                String docId, LocaleId locale) throws IOException {
        Optional<HDocument> docOpt = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (docOpt.isEmpty()) return Optional.empty();
        ProjectType type = resolveProjectType(projectSlug, versionSlug);
        String ext = extensionFor(type);
        HDocument doc = docOpt.get();
        Resource source = toResource(doc);
        TranslationsResource trans = toTranslations(doc, locale);
        byte[] body = writeOne(type, source, trans, locale);
        String contentType = switch (type) {
            case Properties, Utf8Properties -> "text/plain;charset=UTF-8";
            case Xliff -> "application/xliff+xml";
            case Consulo -> "application/x-yaml";
            default -> "text/plain;charset=UTF-8";
        };
        String name = (doc.getDocId() == null ? "doc" : doc.getDocId())
                + "-" + locale.getId() + ext;
        return Optional.of(new Bundle(name, contentType, body));
    }

    /**
     * ZIP of one file per non-obsolete document for the given locale, in
     * the project's configured format.
     */
    @Transactional(readOnly = true)
    public Bundle zipForOfflineTranslation(String projectSlug, String versionSlug,
                                           LocaleId locale) throws IOException {
        ProjectType type = resolveProjectType(projectSlug, versionSlug);
        String ext = extensionFor(type);
        List<HDocument> docs = documentRepository.findByVersion(projectSlug, versionSlug);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            for (HDocument d : docs) {
                Resource source = toResource(d);
                TranslationsResource trans = toTranslations(d, locale);
                byte[] body = writeOne(type, source, trans, locale);
                String name = (d.getDocId() == null ? "doc" : d.getDocId()) + ext;
                zip.putNextEntry(new ZipEntry(name));
                zip.write(body);
                zip.closeEntry();
            }
        }
        String archive = projectSlug + "-" + versionSlug + "-" + locale.getId() + ".zip";
        return new Bundle(archive, "application/zip", buf.toByteArray());
    }

    private ProjectType resolveProjectType(String projectSlug, String versionSlug) {
        Optional<HProjectIteration> iter = iterationRepository
                .findFullByProjectAndSlug(projectSlug, versionSlug);
        if (iter.isPresent() && iter.get().getProject() != null
                && iter.get().getProject().getDefaultProjectType() != null) {
            return iter.get().getProject().getDefaultProjectType();
        }
        return ProjectType.Gettext;
    }

    private static String extensionFor(ProjectType type) {
        return switch (type) {
            case Properties, Utf8Properties -> ".properties";
            case Xliff -> ".xlf";
            case Consulo -> ".yaml";
            case Gettext, Podir, Xml, File -> ".po";
        };
    }

    private byte[] writeOne(ProjectType type, Resource source,
                            TranslationsResource trans, LocaleId locale) throws IOException {
        return switch (type) {
            case Gettext, Podir, Xml, File -> {
                PoWriter2 writer = new PoWriter2();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                writer.writePo(out, "UTF-8", source, trans);
                yield out.toByteArray();
            }
            case Properties -> writeProperties(source, trans, locale, PropWriter.CHARSET.Latin1);
            case Utf8Properties -> writeProperties(source, trans, locale, PropWriter.CHARSET.UTF8);
            case Xliff -> writeXliff(source, trans, locale);
            case Consulo -> writeYaml(source, trans);
        };
    }

    // Package-private so yamlForDoc above can call directly without going through writeOne's enum.
    byte[] writeYaml(Resource source, TranslationsResource trans) throws IOException {
        // Build resId → translation map for O(1) lookups.
        Map<String, String> translatedByResId = new LinkedHashMap<>();
        if (trans.getTextFlowTargets() != null) {
            for (TextFlowTarget t : trans.getTextFlowTargets()) {
                String c = firstContent(t.getContents());
                if (!c.isEmpty()) translatedByResId.put(t.getResId(), c);
            }
        }
        // Preserve source order; key each entry by its original human key.
        Map<String, String> entries = new LinkedHashMap<>();
        for (TextFlow tf : source.getTextFlows()) {
            String translation = translatedByResId.get(tf.getId());
            if (translation == null) continue;
            String humanKey = humanKey(tf);
            if (humanKey == null) continue;
            entries.put(humanKey, translation);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            new ConsuloWriter().write(w, entries);
        }
        return out.toByteArray();
    }

    /** Original human-readable key from PotEntryHeader.context, falling back to resId. */
    private static String humanKey(TextFlow tf) {
        if (tf.getExtensions() != null) {
            PotEntryHeader h = tf.getExtensions().findByType(PotEntryHeader.class);
            if (h != null && h.getContext() != null && !h.getContext().isEmpty()) {
                return h.getContext();
            }
        }
        return tf.getId();
    }

    private byte[] writeProperties(Resource source, TranslationsResource trans,
                                   LocaleId locale, PropWriter.CHARSET charset)
            throws IOException {
        File tmp = File.createTempFile("zanata-export-", ".properties");
        try {
            TranslatedDoc td = new TranslatedDoc(source, trans, locale);
            PropWriter.writeTranslationsFile(td, tmp.toPath(), charset, true, false);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }

    private byte[] writeXliff(Resource source, TranslationsResource trans,
                              LocaleId locale) throws IOException {
        File tmp = File.createTempFile("zanata-export-", ".xlf");
        try {
            XliffWriter.writeFile(tmp.toPath(), source, locale.getId(), trans, true, false);
            return Files.readAllBytes(tmp.toPath());
        } finally {
            tmp.delete();
        }
    }

    /**
     * Minimal TMX 1.4 dump of all non-empty, non-New translations in the
     * iteration for the given locale. Matches the shape of the legacy
     * {@code TranslationsTMXExportStrategy} output enough that tools like
     * OmegaT / Trados can re-ingest it.
     */
    @Transactional(readOnly = true)
    public Bundle tmx(String projectSlug, String versionSlug, LocaleId locale) {
        List<HDocument> docs = documentRepository.findByVersion(projectSlug, versionSlug);
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE tmx SYSTEM \"http://www.lisa.org/tmx/tmx14.dtd\">\n")
                .append("<tmx version=\"1.4\">\n")
                .append("  <header creationtool=\"Zanata\" creationtoolversion=\"4.7.0-SNAPSHOT\" ")
                .append("segtype=\"block\" o-tmf=\"unknown\" adminlang=\"en-US\" srclang=\"en-US\" ")
                .append("datatype=\"plaintext\"/>\n")
                .append("  <body>\n");
        for (HDocument d : docs) {
            List<HTextFlow> flows = textFlowRepository.findByDocument(d.getId());
            for (HTextFlow tf : flows) {
                String source = firstContent(tf.getContents());
                Optional<HTextFlowTarget> targetOpt = targetRepository
                        .findByTextFlowAndLocale(tf.getId(), locale);
                if (targetOpt.isEmpty()) continue;
                HTextFlowTarget target = targetOpt.get();
                ContentState state = target.getState();
                if (state == null || state == ContentState.New
                        || state == ContentState.Rejected) continue;
                String trans = firstContent(target.getContents());
                if (trans.isEmpty()) continue;
                out.append("    <tu tuid=\"").append(escape(d.getDocId()))
                        .append(':').append(escape(tf.getResId())).append("\">\n")
                        .append("      <prop type=\"state\">").append(state.name()).append("</prop>\n")
                        .append("      <tuv xml:lang=\"en-US\"><seg>")
                        .append(escape(source)).append("</seg></tuv>\n")
                        .append("      <tuv xml:lang=\"").append(escape(locale.getId()))
                        .append("\"><seg>").append(escape(trans)).append("</seg></tuv>\n")
                        .append("    </tu>\n");
            }
        }
        out.append("  </body>\n</tmx>\n");
        String name = projectSlug + "-" + versionSlug + "-" + locale.getId() + ".tmx";
        return new Bundle(name, "application/x-tmx+xml",
                out.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Resource toResource(HDocument d) {
        Resource r = new Resource(d.getDocId());
        r.setContentType(d.getContentType() == null
                ? org.zanata.common.ContentType.TextPlain : d.getContentType());
        r.setLang(d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? LocaleId.EN_US : d.getLocale().getLocaleId());
        r.setRevision(d.getRevision());
        List<HTextFlow> flows = textFlowRepository.findByDocument(d.getId());
        List<TextFlow> out = new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) {
            TextFlow xf = new TextFlow(tf.getResId(), r.getLang(),
                    firstContent(tf.getContents()));
            xf.setPlural(Boolean.TRUE.equals(tf.isPlural()));
            xf.setRevision(tf.getRevision());
            // Re-attach the original human key (PotEntryData.context) so
            // YAML/PO export can emit something readable instead of the
            // 32-char resId hash.
            if (tf.getPotEntryData() != null
                    && tf.getPotEntryData().getContext() != null
                    && !tf.getPotEntryData().getContext().isEmpty()) {
                PotEntryHeader header = new PotEntryHeader();
                header.setContext(tf.getPotEntryData().getContext());
                xf.getExtensions(true).add(header);
            }
            out.add(xf);
        }
        r.getTextFlows().addAll(out);
        return r;
    }

    private TranslationsResource toTranslations(HDocument d, LocaleId locale) {
        TranslationsResource tr = new TranslationsResource();
        List<HTextFlow> flows = textFlowRepository.findByDocument(d.getId());
        for (HTextFlow tf : flows) {
            Optional<HTextFlowTarget> opt = targetRepository
                    .findByTextFlowAndLocale(tf.getId(), locale);
            if (opt.isEmpty()) continue;
            HTextFlowTarget hTarget = opt.get();
            // Rejected translations are excluded from every export format so the
            // rejected text is never written; the file falls back to source.
            if (hTarget.getState() == ContentState.Rejected) continue;
            String content = firstContent(hTarget.getContents());
            if (content.isEmpty()) continue;
            TextFlowTarget xt = new TextFlowTarget(tf.getResId());
            xt.setState(hTarget.getState() == null ? ContentState.New : hTarget.getState());
            xt.setContents(java.util.List.of(content));
            xt.setRevision(hTarget.getVersionNum() == null ? 0 : hTarget.getVersionNum());
            xt.setTextFlowRevision(tf.getRevision());
            tr.getTextFlowTargets().add(xt);
        }
        return tr;
    }

    private static String firstContent(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        String c = list.get(0);
        return c == null ? "" : c;
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
