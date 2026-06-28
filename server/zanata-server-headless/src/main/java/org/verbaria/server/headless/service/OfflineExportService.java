package org.verbaria.server.headless.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.zanata.model.HDocument;
import org.zanata.model.HPerson;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.extensions.chrome.ChromeMessage;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.verbaria.server.headless.extension.gettext.GettextExtensions;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

@Service
public class OfflineExportService {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final GettextExtensions gettext;
    private final TextFlowExtensionStore extensionStore;
    private final DocumentLayoutRegistry layoutRegistry;

    public OfflineExportService(ProjectIterationRepository iterationRepository,
                                DocumentRepository documentRepository,
                                TextFlowRepository textFlowRepository,
                                TextFlowTargetRepository targetRepository,
                                GettextExtensions gettext,
                                TextFlowExtensionStore extensionStore,
                                DocumentLayoutRegistry layoutRegistry) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.targetRepository = targetRepository;
        this.textFlowRepository = textFlowRepository;
        this.gettext = gettext;
        this.extensionStore = extensionStore;
        this.layoutRegistry = layoutRegistry;
    }

    public record Bundle(String filename, String contentType, byte[] bytes) {}

    @Transactional(readOnly = true)
    public Optional<Bundle> yamlForDoc(String projectSlug, String versionSlug,
                                       String docId, LocaleId locale) throws IOException {
        Optional<HDocument> docOpt = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (docOpt.isEmpty()) return Optional.empty();
        HDocument doc = docOpt.get();
        Resource source = toResource(doc);
        TranslationsResource trans = toTranslations(doc, locale);
        byte[] body = layoutRegistry.forType("consulo").orElseThrow()
                .writeTranslation(source, trans, locale.getId());
        return Optional.of(new Bundle(docId + ".yaml", "application/x-yaml", body));
    }

    @Transactional(readOnly = true)
    public Optional<Bundle> singleTranslatedDoc(String projectSlug, String versionSlug,
                                                String docId, LocaleId locale) throws IOException {
        Optional<HDocument> docOpt = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (docOpt.isEmpty()) return Optional.empty();
        String type = resolveProjectType(projectSlug, versionSlug);
        DocumentLayout layout = layoutFor(type);
        HDocument doc = docOpt.get();
        Resource source = toResource(doc);
        TranslationsResource trans = toTranslations(doc, locale);
        byte[] body = writeOne(type, source, trans, locale);
        String name = (doc.getDocId() == null ? "doc" : doc.getDocId())
                + "-" + locale.getId() + layout.fileExtension();
        return Optional.of(new Bundle(name, layout.contentType(), body));
    }

    @Transactional(readOnly = true)
    public Bundle zipForOfflineTranslation(String projectSlug, String versionSlug,
                                           LocaleId locale) throws IOException {
        String type = resolveProjectType(projectSlug, versionSlug);
        String ext = layoutFor(type).fileExtension();
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

    private String resolveProjectType(String projectSlug, String versionSlug) {
        Optional<HProjectIteration> iter = iterationRepository
                .findFullByProjectAndSlug(projectSlug, versionSlug);
        if (iter.isPresent() && iter.get().getProject() != null
                && iter.get().getProject().getDefaultProjectType() != null) {
            return iter.get().getProject().getDefaultProjectType();
        }
        return "gettext";
    }

    private DocumentLayout layoutFor(String type) {
        return layoutRegistry.forType(type)
                .orElseThrow(() -> new IllegalStateException(
                        "No document layout for project type: " + type));
    }

    private byte[] writeOne(String type, Resource source,
                            TranslationsResource trans, LocaleId locale) throws IOException {
        return layoutRegistry.forType(type)
                .orElseThrow(() -> new IllegalStateException(
                        "No document layout for project type: " + type))
                .writeTranslation(source, trans, locale.getId());
    }

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

    Resource toResource(HDocument d) {
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
            String ctx = gettext.context(tf);
            if (ctx != null && !ctx.isEmpty()) {
                PotEntryHeader header = new PotEntryHeader();
                header.setContext(ctx);
                xf.getExtensions(true).add(header);
            }
            ConsuloSubFile consulo = extensionStore.get(tf, ConsuloSubFile.class)
                    .orElse(null);
            if (consulo != null
                    && (notEmpty(consulo.getParamNames())
                            || notEmpty(consulo.getParamTypes())
                            || (consulo.getExtension() != null
                                    && !consulo.getExtension().isEmpty()))) {
                xf.getExtensions(true).add(consulo);
            }
            extensionStore.get(tf, ChromeMessage.class)
                    .ifPresent(m -> xf.getExtensions(true).add(m));
            extensionStore.get(tf, SimpleComment.class)
                    .ifPresent(c -> xf.getExtensions(true).add(c));
            out.add(xf);
        }
        r.getTextFlows().addAll(out);
        return r;
    }

    TranslationsResource toTranslations(HDocument d, LocaleId locale) {
        TranslationsResource tr = new TranslationsResource();
        List<HTextFlow> flows = textFlowRepository.findByDocument(d.getId());
        for (HTextFlow tf : flows) {
            Optional<HTextFlowTarget> opt = targetRepository
                    .findByTextFlowAndLocale(tf.getId(), locale);
            if (opt.isEmpty()) continue;
            HTextFlowTarget hTarget = opt.get();
            String content;
            ContentState state;
            if (hTarget.getState() == ContentState.Rejected) {
                HTextFlowTargetHistory good = lastGoodVersion(hTarget);
                if (good == null) continue;
                content = firstContent(good.getContents());
                state = good.getState();
            } else {
                content = firstContent(hTarget.getContents());
                state = hTarget.getState() == null ? ContentState.New : hTarget.getState();
            }
            if (content.isEmpty()) continue;
            TextFlowTarget xt = new TextFlowTarget(tf.getResId());
            xt.setState(state);
            xt.setContents(List.of(content));
            xt.setRevision(hTarget.getVersionNum() == null ? 0 : hTarget.getVersionNum());
            xt.setTextFlowRevision(tf.getRevision());
            HPerson who = hTarget.getTranslator() != null
                    ? hTarget.getTranslator() : hTarget.getLastModifiedBy();
            if (who != null) {
                xt.setTranslator(new Person(who.getEmail(), who.getName()));
            }
            tr.getTextFlowTargets().add(xt);
        }
        return tr;
    }

    private static String firstContent(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        String c = list.get(0);
        return c == null ? "" : c;
    }

    private static boolean notEmpty(List<String> list) {
        return list != null && !list.isEmpty();
    }

    public static HTextFlowTargetHistory lastGoodVersion(HTextFlowTarget rejected) {
        String rejectedText = firstContent(rejected.getContents());
        return rejected.getHistory().values().stream()
                .filter(h -> h.getState() == ContentState.Approved
                        || h.getState() == ContentState.Translated)
                .filter(h -> {
                    String c = firstContent(h.getContents());
                    return !c.isEmpty() && !c.equals(rejectedText);
                })
                .max(Comparator.comparingInt(
                        h -> h.getVersionNum() == null ? 0 : h.getVersionNum()))
                .orElse(null);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
