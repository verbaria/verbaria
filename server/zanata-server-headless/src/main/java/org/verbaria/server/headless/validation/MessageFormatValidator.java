package org.verbaria.server.headless.validation;

import java.util.List;
import java.util.Locale;

import org.verbaria.server.headless.message.MessageEvaluator;
import org.verbaria.server.headless.message.MessageInfo;

/**
 * A {@link LanguageValidator} that checks a message string parses as its
 * project's message format (Java / ICU4J / printf). A syntax error is reported
 * as a single {@link ValidationIssue.Severity#ERROR} spanning the whole text.
 * Built per editor row around the project's {@link MessageEvaluator}, so it is
 * not a singleton bean.
 */
public class MessageFormatValidator implements LanguageValidator {

    private final MessageEvaluator evaluator;

    public MessageFormatValidator(MessageEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    @Override
    public List<ValidationIssue> validate(String text, Locale locale) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        MessageInfo info = evaluator.analyze(text, locale);
        if (info.isValid()) {
            return List.of();
        }
        String message = info.getError() == null
                ? "Invalid message format" : info.getError();
        return List.of(new ValidationIssue(0, text.length(), message,
                List.of(), "message-format", ValidationIssue.Severity.ERROR));
    }
}
