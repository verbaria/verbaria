package org.verbaria.server.headless.ext;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.ui.TextFlowGateway;
import org.verbaria.server.ui.TextFlowSnapshot;
import org.zanata.model.HTextFlow;
import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

@Component
public class TextFlowGatewayImpl implements TextFlowGateway {

    private final TextFlowRepository textFlowRepository;
    private final TextFlowExtensionStore extensionStore;

    public TextFlowGatewayImpl(TextFlowRepository textFlowRepository,
            TextFlowExtensionStore extensionStore) {
        this.textFlowRepository = textFlowRepository;
        this.extensionStore = extensionStore;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> documentTextFlowIds(long documentId) {
        return textFlowRepository.findByDocument(documentId).stream()
                .map(HTextFlow::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TextFlowSnapshot snapshot(long textFlowId) {
        HTextFlow flow = loadWithExtensions(textFlowId).orElse(null);
        if (flow == null) {
            return new TextFlowSnapshot(null, null, List.of());
        }
        String projectType = flow.getDocument() == null
                || flow.getDocument().getProjectIteration() == null ? null
                : flow.getDocument().getProjectIteration()
                        .getEffectiveProjectType();
        return new TextFlowSnapshot(projectType, firstContent(flow),
                extensionStore.all(flow));
    }

    @Override
    @Transactional(readOnly = true)
    public String sourceText(long textFlowId) {
        return textFlowRepository.findById(textFlowId)
                .map(TextFlowGatewayImpl::firstContent).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public <T extends TextFlowExtension> Optional<T> extension(long textFlowId,
            Class<T> type) {
        return loadWithExtensions(textFlowId)
                .flatMap(flow -> extensionStore.get(flow, type));
    }

    @Override
    @Transactional
    public void putExtension(long textFlowId, TextFlowExtension extension) {
        HTextFlow flow = loadWithExtensions(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        extensionStore.put(flow, extension);
        textFlowRepository.save(flow);
    }

    @Override
    @Transactional
    public void update(long textFlowId, String sourceText,
            TextFlowExtension extension) {
        HTextFlow flow = loadWithExtensions(textFlowId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "TextFlow not found: " + textFlowId));
        if (sourceText != null && !sourceText.equals(firstContent(flow))) {
            flow.setContents(List.of(sourceText));
            flow.setRevision(flow.getRevision() + 1);
        }
        if (extension != null) {
            extensionStore.put(flow, extension);
        }
        textFlowRepository.save(flow);
    }

    private Optional<HTextFlow> loadWithExtensions(long textFlowId) {
        return textFlowRepository.findWithExtensions(List.of(textFlowId))
                .stream().findFirst();
    }

    private static String firstContent(HTextFlow flow) {
        return flow.getContents() == null || flow.getContents().isEmpty()
                || flow.getContents().get(0) == null
                ? "" : flow.getContents().get(0);
    }
}
