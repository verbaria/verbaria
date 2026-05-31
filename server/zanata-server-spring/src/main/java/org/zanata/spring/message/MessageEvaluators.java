package org.zanata.spring.message;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.zanata.common.MessageEvaluateType;

/**
 * Registry of {@link MessageEvaluator} beans by {@link MessageEvaluateType}.
 * Spring injects every {@link MessageEvaluator} component; new message syntaxes
 * are supported simply by adding another {@code @Component} implementation.
 */
@Component
public class MessageEvaluators {

    private final Map<MessageEvaluateType, MessageEvaluator> byType =
            new EnumMap<>(MessageEvaluateType.class);

    public MessageEvaluators(List<MessageEvaluator> evaluators) {
        for (MessageEvaluator evaluator : evaluators) {
            byType.put(evaluator.type(), evaluator);
        }
    }

    /**
     * The evaluator for {@code type}, or {@code null} for
     * {@link MessageEvaluateType#NONE} (or a type with no registered evaluator).
     */
    public MessageEvaluator forType(MessageEvaluateType type) {
        if (type == null || type == MessageEvaluateType.NONE) {
            return null;
        }
        return byType.get(type);
    }
}
