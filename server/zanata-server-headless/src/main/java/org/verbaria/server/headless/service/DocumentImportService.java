package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.event.ProjectStatsChangedEvent;
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
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
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
    private final LocaleRepository localeRepository;
    private final TextFlowExtensionStore extensionStore;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentImportService(ProjectIterationRepository iterationRepository,
                                 DocumentRepository documentRepository,
                                 TextFlowRepository textFlowRepository,
                                 TextFlowTargetRepository textFlowTargetRepository,
                                 LocaleRepository localeRepository,
                                 TextFlowExtensionStore extensionStore,
                                 ApplicationEventPublisher eventPublisher) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.localeRepository = localeRepository;
        this.extensionStore = extensionStore;
        this.eventPublisher = eventPublisher;
    }

    public record ImportResult(HDocument document, boolean created) {}

    public record TranslationsImportResult(int accepted, int skipped,
            int unchanged) {}

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
        return importSource(projectSlug, versionSlug, docId, resource, actor,
                false);
    }

    /**
     * @param force when true, overwrite the source-locale targets even if the
     *              pushed content/state is identical (bypasses the value check).
     */
    public ImportResult importSource(String projectSlug,
                                     String versionSlug,
                                     String docId,
                                     Resource resource,
                                     HAccount actor,
                                     boolean force) {
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

        int nextRevision = created ? doc.getRevision() : doc.getRevision() + 1;
        boolean sourceChanged = false;

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
                tf.setRevision(nextRevision);
                doc.getAllTextFlows().put(incoming.getId(), tf);
                sourceChanged = true;
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
                    tf.setRevision(nextRevision);
                    sourceChanged = true;
                }
            }
            // Persist gettext-style metadata (context, references, flags).
            // For .properties uploads SourceUploadService stuffs the original
            // property key into context, so the editor's Details panel can
            // still surface the human-readable identifier.
            extensionStore.apply(incoming, tf);
            newOrder.add(tf);
            pos++;
        }

        // Mark any leftover flows as obsolete.
        for (HTextFlow stale : byResId.values()) {
            if (!stale.isObsolete()) {
                stale.setObsolete(true);
                sourceChanged = true;
            }
        }

        if (!created && (sourceChanged || force)) {
            doc.setRevision(nextRevision);
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
        ensureSourceLocaleTargets(srcLocale, savedDoc.getTextFlows(), actor,
                force);

        eventPublisher.publishEvent(new ProjectStatsChangedEvent(projectSlug));
        return new ImportResult(savedDoc, created);
    }

    /**
     * Ensure every (non-empty) source flow has a target in the source locale,
     * with content equal to the source. State is Approved for an admin importer
     * and Translated otherwise.
     */
    private void ensureSourceLocaleTargets(HLocale srcLocale,
            List<HTextFlow> flows, HAccount actor, boolean force) {
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
                    .orElse(null);
            // Unchanged re-push: leave the existing source-locale target alone
            // so its version/author/lastChanged stay put — unless --force.
            if (!force && target != null && isUnchanged(target, contents, state)) {
                continue;
            }
            if (target == null) {
                target = new HTextFlowTarget(tf, srcLocale);
            }
            target.setContents(contents);
            target.setState(state);
            target.setTextFlowRevision(tf.getRevision());
            // Attribute the source push to the pusher so the editor's History
            // panel shows an author instead of a blank "by".
            if (author != null) {
                target.setTranslator(author);
                target.setLastModifiedBy(author);
            }
            if (force) {
                target.setLastChanged(new Date());
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
        return importTranslations(projectSlug, versionSlug, docId, localeId,
                translations, actingAs, false);
    }

    /**
     * @param force when true, overwrite each matched target even if the pushed
     *              content/state is identical (bypasses the value check), fully
     *              re-stamping version, author and source revision.
     */
    @Transactional
    public TranslationsImportResult importTranslations(String projectSlug,
                                                       String versionSlug,
                                                       String docId,
                                                       String localeId,
                                                       TranslationsResource translations,
                                                       HAccount actingAs,
                                                       boolean force) {
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
        int unchanged = 0;
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

            ContentState state = incoming.getState() == null
                    ? ContentState.Translated : incoming.getState();
            // Review states (approved/rejected) require admin rights; for
            // everyone else, downgrade an approved push to translated.
            if ((state == ContentState.Approved
                    || state == ContentState.Rejected)
                    && !Roles.isCurrentUserAdmin()) {
                state = ContentState.Translated;
            }

            HTextFlowTarget target = textFlowTargetRepository
                    .findByTextFlowAndLocale(tf.getId(), locale.getLocaleId())
                    .orElse(null);
            // A re-push of an identical translation must be a no-op: leave the
            // row exactly as it was so the version, author and lastChanged don't
            // churn on every push (which otherwise floods history with
            // non-edits). --force overrides this and always rewrites.
            if (!force && target != null && isUnchanged(target, contents, state)) {
                unchanged++;
                continue;
            }
            if (target == null) {
                target = new HTextFlowTarget(tf, locale);
            }
            // History is maintained by HTextFlowTarget's @PreUpdate listener,
            // which snapshots the prior state under the *old* versionNum exactly
            // when the row actually changes. Writing a history row here as well
            // duplicated it — and on an unchanged re-push the version never
            // advanced, leaving an orphan row that collided with the next edit's
            // listener write on the (target_id, version_num) unique key.
            target.setContents(contents);
            target.setState(state);
            target.setTextFlowRevision(tf.getRevision());
            if (translator != null) {
                target.setTranslator(translator);
                target.setLastModifiedBy(translator);
            }
            // --force may carry identical content, which Hibernate would treat
            // as no change and never write; touch lastChanged so the override
            // actually advances the version and re-stamps the author.
            if (force) {
                target.setLastChanged(new Date());
            }
            textFlowTargetRepository.save(target);
            accepted++;
        }
        eventPublisher.publishEvent(new ProjectStatsChangedEvent(projectSlug));
        return new TranslationsImportResult(accepted, skipped, unchanged);
    }

    /**
     * Whether the stored target already holds exactly this content and state, so
     * a push carrying it would only re-stamp the version/author for nothing.
     */
    private static boolean isUnchanged(HTextFlowTarget target,
            List<String> contents, ContentState state) {
        return target.getState() == state
                && contents.equals(target.getContents());
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
