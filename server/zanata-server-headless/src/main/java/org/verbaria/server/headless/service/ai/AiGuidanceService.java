package org.verbaria.server.headless.service.ai;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.LocaleId;
import org.zanata.common.MessageEvaluateType;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;

@Service
public class AiGuidanceService {

    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final LocaleRepository localeRepository;

    public AiGuidanceService(DocumentRepository documentRepository,
            TextFlowRepository textFlowRepository,
            LocaleRepository localeRepository) {
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.localeRepository = localeRepository;
    }

    @Transactional(readOnly = true)
    public String forDocument(Long docId, LocaleId locale) {
        HProject project = documentRepository.findById(docId)
                .map(HDocument::getProjectIteration)
                .map(HProjectIteration::getProject)
                .orElse(null);
        return build(project, locale);
    }

    @Transactional(readOnly = true)
    public String forTextFlow(Long textFlowId, LocaleId locale) {
        HProject project = textFlowRepository.findById(textFlowId)
                .map(HTextFlow::getDocument)
                .map(HDocument::getProjectIteration)
                .map(HProjectIteration::getProject)
                .orElse(null);
        return build(project, locale);
    }

    private String build(HProject project, LocaleId locale) {
        StringBuilder sb = new StringBuilder();
        if (project != null) {
            if (project.getSlug() != null) {
                sb.append("Project ID: ").append(project.getSlug()).append('\n');
            }
            if (project.getName() != null && !project.getName().isBlank()) {
                sb.append("Project Name: ").append(project.getName()).append('\n');
            }
            String format = describeFormat(project.getMessageEvaluateType());
            if (format != null) {
                sb.append("Message format: ").append(format).append('\n');
            }
        }
        String prompt = localeRepository.findByLocaleId(locale)
                .map(HLocale::getAiPrompt).orElse(null);
        if (prompt != null && !prompt.isBlank()) {
            sb.append(prompt.strip()).append('\n');
        }
        String out = sb.toString().strip();
        return out.isEmpty() ? null : out;
    }

    private static String describeFormat(MessageEvaluateType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case JAVA_MESSAGE_FORMAT -> "Java MessageFormat — keep placeholders "
                    + "like {0}, {1}, {0,number,integer}, {0,date} exactly as in "
                    + "the source, and escape literal single quotes by doubling them ('').";
            case ICU4J_MESSAGE_FORMAT -> "ICU MessageFormat — keep placeholders "
                    + "{0}/{name}, the # symbol, and plural/select blocks "
                    + "(e.g. one {...} other {...}) exactly as in the source.";
            case C_PRINTF -> "C/printf — keep format specifiers like %s, %d, "
                    + "%1$s, %.2f exactly as in the source.";
            case NONE -> null;
        };
    }
}
