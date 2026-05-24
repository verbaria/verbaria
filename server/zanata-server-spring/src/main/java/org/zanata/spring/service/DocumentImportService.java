package org.zanata.spring.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.model.po.HPotEntryData;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.util.HashUtil;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetHistoryRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;

/**
 * Service that handles the heavier upsert logic used by the CLI bridge:
 *   - importing/updating a source document (Resource -> HDocument + HTextFlow tree)
 *   - importing/updating translations (TranslationsResource -> HTextFlowTarget tree)
 *
 * Kept out of the controller so the REST handler stays small and the
 * transactional boundaries are explicit.
 */
@Service
public class DocumentImportService {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final LocaleRepository localeRepository;

    public DocumentImportService(ProjectIterationRepository iterationRepository,
                                 DocumentRepository documentRepository,
                                 TextFlowRepository textFlowRepository,
                                 TextFlowTargetRepository textFlowTargetRepository,
                                 TextFlowTargetHistoryRepository historyRepository,
                                 LocaleRepository localeRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.historyRepository = historyRepository;
        this.localeRepository = localeRepository;
    }

    public record ImportResult(HDocument document, boolean created) {}

    public record TranslationsImportResult(int accepted, int skipped) {}

    /**
     * Upsert a source document for the given project iteration. Matches
     * incoming TextFlow entries to existing HTextFlow rows by resId, updates
     * content/wordCount/plural/pos, creates new ones for unseen resIds, and
     * marks unmatched existing flows as obsolete.
     */
    @Transactional
    public ImportResult importSource(String projectSlug,
                                     String versionSlug,
                                     String docId,
                                     Resource resource) {
        HProjectIteration iter = iterationRepository
                .findByProjectAndSlug(projectSlug, versionSlug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project iteration not found: " + projectSlug + "/" + versionSlug));

        HLocale srcLocale = resolveLocale(resource.getLang());

        Optional<HDocument> existing =
                documentRepository.findByVersionAndDocId(projectSlug, versionSlug, docId);
        HDocument doc;
        boolean created;
        if (existing.isPresent()) {
            doc = existing.get();
            doc.setObsolete(false);
            doc.setContentType(resource.getContentType() == null
                    ? ContentType.TextPlain : resource.getContentType());
            doc.setLocale(srcLocale);
            doc.incrementRevision();
            created = false;
        } else {
            doc = new HDocument(
                    docId,
                    extractName(docId),
                    extractPath(docId),
                    resource.getContentType() == null
                            ? ContentType.TextPlain : resource.getContentType(),
                    srcLocale);
            doc.setProjectIteration(iter);
            doc.setRevision(1);
            iter.getAllDocuments().put(docId, doc);
            iter.getDocuments().put(docId, doc);
            doc = documentRepository.save(doc);
            created = true;
        }

        // Index existing flows by resId for fast matching.
        Map<String, HTextFlow> byResId = new HashMap<>();
        for (HTextFlow existingTf : doc.getAllTextFlows().values()) {
            byResId.put(existingTf.getResId(), existingTf);
        }

        List<TextFlow> incomingFlows = resource.getTextFlows() == null
                ? List.of() : resource.getTextFlows();

        List<HTextFlow> newOrder = new ArrayList<>(incomingFlows.size());
        int pos = 0;
        for (TextFlow incoming : incomingFlows) {
            if (incoming.getId() == null || incoming.getId().isEmpty()) continue;
            // Defensive normalization: if a client (legacy CLI .properties
            // push, custom REST caller, etc.) sends a raw property key as
            // the TextFlow id, hash it now so the resId column never
            // overflows or collides. The original key is preserved in
            // PotEntryHeader.context (added below by applyPotEntryHeader).
            normalizeIncomingId(incoming);
            HTextFlow tf = byResId.remove(incoming.getId());
            if (tf == null) {
                tf = new HTextFlow(doc, incoming.getId());
                tf.setPos(pos);
                tf.setPlural(incoming.isPlural());
                tf.setContents(incoming.getContents() == null
                        ? List.of("") : incoming.getContents());
                tf.setRevision(doc.getRevision());
                doc.getAllTextFlows().put(incoming.getId(), tf);
            } else {
                List<String> oldContents = tf.getContents();
                List<String> newContents = incoming.getContents() == null
                        ? List.of("") : incoming.getContents();
                boolean contentChanged = !oldContents.equals(newContents);
                boolean pluralChanged = tf.isPlural() != incoming.isPlural();
                tf.setObsolete(false);
                tf.setPos(pos);
                tf.setPlural(incoming.isPlural());
                tf.setContents(newContents);
                if (contentChanged || pluralChanged) {
                    tf.setRevision(doc.getRevision());
                }
            }
            // Persist gettext-style metadata (context, references, flags).
            // For .properties uploads SourceUploadService stuffs the original
            // property key into context, so the editor's Details panel can
            // still surface the human-readable identifier.
            applyPotEntryHeader(tf, incoming);
            newOrder.add(tf);
            pos++;
        }

        // Mark any leftover flows as obsolete.
        for (HTextFlow stale : byResId.values()) {
            stale.setObsolete(true);
        }

        // Replace the live textFlows list (only the non-obsolete flows in the new order).
        doc.getTextFlows().clear();
        doc.getTextFlows().addAll(newOrder);

        return new ImportResult(documentRepository.save(doc), created);
    }

    /**
     * Push translations: upsert HTextFlowTarget rows for each incoming
     * TextFlowTarget, matched by resId within the supplied document.
     * Existing non-null content is copied to history before being overwritten.
     */
    @Transactional
    public TranslationsImportResult importTranslations(String projectSlug,
                                                       String versionSlug,
                                                       String docId,
                                                       String localeId,
                                                       TranslationsResource translations,
                                                       HAccount actingAs) {
        HDocument doc = documentRepository
                .findByVersionAndDocId(projectSlug, versionSlug, docId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found: " + docId));
        HLocale locale = resolveLocale(new LocaleId(localeId));

        Map<String, HTextFlow> flowsByResId = new HashMap<>();
        for (HTextFlow tf : textFlowRepository.findByDocument(doc.getId())) {
            flowsByResId.put(tf.getResId(), tf);
        }

        HPerson translator = actingAs == null ? null : actingAs.getPerson();
        int accepted = 0;
        int skipped = 0;
        List<TextFlowTarget> targets = translations.getTextFlowTargets() == null
                ? List.of() : translations.getTextFlowTargets();
        for (TextFlowTarget incoming : targets) {
            if (incoming.getResId() == null) {
                skipped++;
                continue;
            }
            HTextFlow tf = flowsByResId.get(incoming.getResId());
            if (tf == null) {
                skipped++;
                continue;
            }
            List<String> contents = incoming.getContents() == null
                    ? List.of() : incoming.getContents();
            if (contents.isEmpty()
                    || contents.stream().allMatch(s -> s == null || s.isEmpty())) {
                skipped++;
                continue;
            }

            HTextFlowTarget target = textFlowTargetRepository
                    .findByTextFlowAndLocale(tf.getId(), locale.getLocaleId())
                    .orElse(null);
            if (target == null) {
                target = new HTextFlowTarget(tf, locale);
            } else {
                // Snapshot prior state into history if there was meaningful content.
                List<String> prior = target.getContents();
                boolean hasPrior = prior != null && prior.stream()
                        .anyMatch(s -> s != null && !s.isEmpty());
                if (hasPrior) {
                    HTextFlowTargetHistory history = new HTextFlowTargetHistory(target);
                    history.setVersionNum(target.getVersionNum() == null
                            ? 0 : target.getVersionNum());
                    historyRepository.save(history);
                }
            }
            target.setContents(contents);
            ContentState state = incoming.getState() == null
                    ? ContentState.Translated : incoming.getState();
            target.setState(state);
            target.setTextFlowRevision(tf.getRevision());
            if (translator != null) {
                target.setTranslator(translator);
                target.setLastModifiedBy(translator);
            }
            textFlowTargetRepository.save(target);
            accepted++;
        }
        return new TranslationsImportResult(accepted, skipped);
    }

    /**
     * Replace {@code incoming.id} with a hash when it's clearly not already
     * one (anything but a lowercase 32-char hex string is treated as raw),
     * and remember the original id in {@code PotEntryHeader.context} so the
     * editor's Details panel can still surface it.
     */
    private static void normalizeIncomingId(TextFlow incoming) {
        String id = incoming.getId();
        if (id == null || id.isEmpty()) return;
        if (looksLikeHexHash(id)) return;
        String original = id;
        incoming.setId(HashUtil.generateHash(original));
        PotEntryHeader header = incoming.getExtensions(true)
                .findByType(PotEntryHeader.class);
        if (header == null) {
            header = new PotEntryHeader();
            header.setContext(original);
            incoming.getExtensions(true).add(header);
        } else if (header.getContext() == null || header.getContext().isEmpty()) {
            header.setContext(original);
        }
    }

    private static boolean looksLikeHexHash(String s) {
        if (s.length() != 32) return false;
        for (int i = 0; i < 32; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return false;
        }
        return true;
    }

    private static void applyPotEntryHeader(HTextFlow tf, TextFlow incoming) {
        PotEntryHeader header = incoming.getExtensions() == null
                ? null : incoming.getExtensions().findByType(PotEntryHeader.class);
        if (header == null) {
            return;
        }
        HPotEntryData data = tf.getPotEntryData();
        if (data == null) {
            data = new HPotEntryData();
            tf.setPotEntryData(data);
        }
        if (header.getContext() != null && !header.getContext().isEmpty()) {
            data.setContext(header.getContext());
        }
        if (header.getFlags() != null && !header.getFlags().isEmpty()) {
            data.setFlags(String.join(",", header.getFlags()));
        }
        if (header.getReferences() != null && !header.getReferences().isEmpty()) {
            data.setReferences(String.join(",", header.getReferences()));
        }
    }

    private HLocale resolveLocale(LocaleId localeId) {
        LocaleId resolved = localeId == null ? LocaleId.EN_US : localeId;
        return localeRepository.findByLocaleId(resolved)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not enabled on server: " + resolved.getId()));
    }

    private static String extractName(String docId) {
        int idx = docId.lastIndexOf('/');
        return idx < 0 ? docId : docId.substring(idx + 1);
    }

    private static String extractPath(String docId) {
        int idx = docId.lastIndexOf('/');
        if (idx < 0) return "";
        if (idx == 0) return "/";
        return docId.substring(0, idx + 1);
    }
}
