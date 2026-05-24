package org.zanata.spring.vaadin.translate;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetHistoryRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;

@Service
public class TranslationEditService {

    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final LocaleRepository localeRepository;

    public TranslationEditService(TextFlowRepository textFlowRepository,
                                  TextFlowTargetRepository targetRepository,
                                  TextFlowTargetHistoryRepository historyRepository,
                                  LocaleRepository localeRepository) {
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.historyRepository = historyRepository;
        this.localeRepository = localeRepository;
    }

    @Transactional
    public void updateSource(Long textFlowId, String newSource) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        String oldSource = textFlow.getContents() == null || textFlow.getContents().isEmpty()
                ? "" : textFlow.getContents().get(0);
        String fresh = newSource == null ? "" : newSource;
        if (oldSource.equals(fresh)) return;
        textFlow.setContents(List.of(fresh));
        textFlow.setRevision(textFlow.getRevision() + 1);
        textFlowRepository.save(textFlow);
    }

    @Transactional
    public ContentState changeState(Long textFlowId, LocaleId localeId, ContentState newState) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HLocale locale = localeRepository.findByLocaleId(localeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not found: " + localeId.getId()));
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseThrow(() -> new IllegalStateException(
                        "No translation to change state — type and save first"));
        boolean hasContent = target.getContents() != null
                && !target.getContents().isEmpty()
                && target.getContents().get(0) != null
                && !target.getContents().get(0).isBlank();
        if (!hasContent) {
            throw new IllegalStateException(
                    "Cannot change state of an empty translation");
        }
        if (target.getState() != null && target.getState() != ContentState.New) {
            historyRepository.save(new HTextFlowTargetHistory(target));
        }
        target.setState(newState);
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
        return target.getState();
    }

    @Transactional
    public ContentState save(Long textFlowId, LocaleId localeId, String newContent) {
        HTextFlow textFlow = textFlowRepository.findById(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        HLocale locale = localeRepository.findByLocaleId(localeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Locale not found: " + localeId.getId()));
        HTextFlowTarget target = targetRepository
                .findByTextFlowAndLocale(textFlowId, localeId)
                .orElseGet(() -> new HTextFlowTarget(textFlow, locale));

        if (target.getId() != null && target.getState() != null
                && target.getState() != ContentState.New) {
            HTextFlowTargetHistory hist = new HTextFlowTargetHistory(target);
            historyRepository.save(hist);
        }

        target.setContents(List.of(newContent == null ? "" : newContent));
        target.setState(ContentState.Translated);
        target.setTextFlowRevision(textFlow.getRevision());
        targetRepository.save(target);
        return target.getState();
    }
}
