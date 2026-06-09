package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.verbaria.server.headless.service.ai.AiGuidanceService;
import org.verbaria.server.headless.service.ai.TranslationProvider;
import org.verbaria.server.headless.service.ai.TranslationProviderRegistry;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.rest.dto.TranslationSourceType;

/**
 * Runs an AI translation and persists the result. The translation is saved
 * (state Translated) and, when the editor may review the locale, immediately
 * approved — so a reviewer/admin doesn't have to save and approve by hand.
 *
 * <p>The provider call (a network round-trip) runs outside any transaction; the
 * DB reads/writes are delegated to {@link TranslationEditService} so each runs
 * in its own short transaction.</p>
 */
@Service
public class AiTranslationService {

    private final TranslationProviderRegistry registry;
    private final TranslationEditService editService;
    private final ReviewPermissionService reviewPermission;
    private final AiGuidanceService guidanceService;

    public AiTranslationService(TranslationProviderRegistry registry,
            TranslationEditService editService,
            ReviewPermissionService reviewPermission,
            AiGuidanceService guidanceService) {
        this.registry = registry;
        this.editService = editService;
        this.reviewPermission = reviewPermission;
        this.guidanceService = guidanceService;
    }

    /**
     * @param applied whether the suggestion was persisted (saved/approved). When
     *                {@code false} the {@code content} is only offered for the
     *                user to Save/Approve by hand (a translation already existed).
     */
    public record OneResult(String content, ContentState state,
            boolean applied) {}

    public record BulkResult(int translated, int approved) {}

    public OneResult translateOne(Long textFlowId, LocaleId locale,
            String providerId, String editorUsername) {
        TranslationProvider provider = provider(providerId);
        TranslationEditService.AiSource s = editService.aiSource(textFlowId);
        String guidance = guidanceService.forTextFlow(textFlowId, locale);
        String out = provider.translate(s.source(), s.sourceLocale(), locale,
                s.context(), guidance);
        if (out == null || out.isBlank()) {
            return new OneResult(null, null, false);
        }
        // Only auto-apply into an empty slot; never clobber an existing
        // translation — the user must Save/Approve that deliberately.
        if (editService.hasTranslation(textFlowId, locale)) {
            return new OneResult(out, null, false);
        }
        ContentState state = editService.save(textFlowId, locale, out,
                editorUsername, TranslationSourceType.MACHINE_TRANS);
        if (reviewPermission.canReview(editorUsername, locale)) {
            state = editService.changeState(textFlowId, locale,
                    ContentState.Approved);
        }
        return new OneResult(out, state, true);
    }

    public String suggestOne(Long textFlowId, LocaleId locale,
            String providerId) {
        TranslationProvider provider = provider(providerId);
        TranslationEditService.AiSource s = editService.aiSource(textFlowId);
        String guidance = guidanceService.forTextFlow(textFlowId, locale);
        String out = provider.translate(s.source(), s.sourceLocale(), locale,
                s.context(), guidance);
        return out == null || out.isBlank() ? null : out;
    }

    public BulkResult translateUntranslated(Long docId, LocaleId locale,
            String providerId, String editorUsername, int limit,
            ContentState targetState) {
        TranslationProvider provider = provider(providerId);
        List<TranslationEditService.AiSourceRow> rows =
                editService.aiUntranslatedSources(docId, locale, limit);
        if (rows.isEmpty()) {
            return new BulkResult(0, 0);
        }
        String guidance = guidanceService.forDocument(docId, locale);
        List<TranslationProvider.TranslationRequest> reqs =
                new ArrayList<>(rows.size());
        for (TranslationEditService.AiSourceRow r : rows) {
            reqs.add(new TranslationProvider.TranslationRequest(
                    r.source(), r.sourceLocale(), locale, r.context(), guidance));
        }
        List<String> outs = provider.translateBatch(reqs);
        boolean review = reviewPermission.canReview(editorUsername, locale);
        ContentState applied = targetState == ContentState.Approved && review
                ? ContentState.Approved : ContentState.NeedReview;
        int translated = 0;
        int approved = 0;
        for (int i = 0; i < rows.size(); i++) {
            String out = i < outs.size() ? outs.get(i) : null;
            if (out == null || out.isBlank()) {
                continue;
            }
            Long id = rows.get(i).textFlowId();
            editService.save(id, locale, out, editorUsername,
                    TranslationSourceType.MACHINE_TRANS);
            translated++;
            editService.changeState(id, locale, applied);
            if (applied == ContentState.Approved) {
                approved++;
            }
        }
        return new BulkResult(translated, approved);
    }

    private TranslationProvider provider(String providerId) {
        return registry.byId(providerId).orElseThrow(
                () -> new IllegalArgumentException(
                        "Unknown translation provider: " + providerId));
    }
}
