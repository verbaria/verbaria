package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.verbaria.server.headless.security.Roles;
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
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.util.HashUtil;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

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
                                     Resource resource,
                                     HAccount actor) {
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
            applyConsuloSubFile(tf, incoming);
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

        // saveAndFlush (not save): on a first push every text flow is brand new
        // and transient. save() routes to em.merge(), which cascade-persists
        // *copies* of those flows into the returned entity while the originals
        // in `newOrder` stay transient — so building source-locale targets
        // against them later trips Hibernate's "references an unsaved transient
        // instance of HTextFlow". Flushing here forces the cascade INSERTs (and
        // ID assignment), and we then target the managed flows off `savedDoc`.
        HDocument savedDoc = documentRepository.saveAndFlush(doc);

        // The source language is the project's base / "key-sharing" locale —
        // the only place keys (text flows) are ever defined — and is itself an
        // editable, reviewable localization. Give every source flow a target in
        // the source locale so the base shows up and reviews like any locale
        // instead of a perpetual "New". Approved for an admin importer (the
        // authoritative base), Translated otherwise; mirrors the push --approve
        // / non-admin-downgrade rule. Use the managed flows on savedDoc (not the
        // possibly-transient newOrder instances) so each target references a
        // persistent HTextFlow.
        ensureSourceLocaleTargets(srcLocale, savedDoc.getTextFlows(), actor);

        return new ImportResult(savedDoc, created);
    }

    /**
     * Ensure every (non-empty) source flow has a target in the source locale,
     * with content equal to the source. State is Approved for an admin importer
     * and Translated otherwise.
     */
    private void ensureSourceLocaleTargets(HLocale srcLocale,
            List<HTextFlow> flows, HAccount actor) {
        ContentState state = Roles.isCurrentUserAdmin()
                ? ContentState.Approved : ContentState.Translated;
        HPerson author = actor == null ? null : actor.getPerson();
        for (HTextFlow tf : flows) {
            List<String> contents = tf.getContents();
            if (contents == null || contents.isEmpty()
                    || contents.get(0) == null || contents.get(0).isEmpty()) {
                continue;
            }
            HTextFlowTarget target = textFlowTargetRepository
                    .findByTextFlowAndLocale(tf.getId(), srcLocale.getLocaleId())
                    .orElseGet(() -> new HTextFlowTarget(tf, srcLocale));
            target.setContents(contents);
            target.setState(state);
            target.setTextFlowRevision(tf.getRevision());
            // Attribute the source push to the pusher so the editor's History
            // panel shows an author instead of a blank "by".
            if (author != null) {
                target.setTranslator(author);
                target.setLastModifiedBy(author);
            }
            textFlowTargetRepository.save(target);
        }
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
            // Source import hashes the resId (see normalizeIncomingId), keeping
            // the original key in PotEntryHeader.context. Incoming targets carry
            // the *raw* key (e.g. a property name), so apply the same hashing
            // before matching — otherwise every translation is skipped and
            // nothing lands in the DB.
            HTextFlow tf = flowsByResId.get(incoming.getResId());
            if (tf == null && !looksLikeHexHash(incoming.getResId())) {
                tf = flowsByResId.get(HashUtil.generateHash(
                        incoming.getResId().toLowerCase(java.util.Locale.ROOT)));
            }
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
            // Review states (approved/rejected) require admin rights; for
            // everyone else, downgrade an approved push to translated.
            if ((state == ContentState.Approved
                    || state == ContentState.Rejected)
                    && !Roles.isCurrentUserAdmin()) {
                state = ContentState.Translated;
            }
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
        incoming.setId(HashUtil.generateHash(
                original.toLowerCase(java.util.Locale.ROOT)));
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

    /**
     * Persist the consulo sub-file extension so source pull can recreate the
     * exact raw file and the editor can show/change it. Stored even when empty
     * (an extensionless raw file) so its presence still flags a raw sub-file.
     */
    private static void applyConsuloSubFile(HTextFlow tf, TextFlow incoming) {
        ConsuloSubFile cf = incoming.getExtensions() == null ? null
                : incoming.getExtensions().findByType(ConsuloSubFile.class);
        if (cf != null) {
            tf.setConsuloFileExt(
                    cf.getExtension() == null ? "" : cf.getExtension());
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
