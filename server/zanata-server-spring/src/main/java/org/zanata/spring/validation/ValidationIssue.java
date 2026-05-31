package org.zanata.spring.validation;

import java.util.List;

public record ValidationIssue(
        int offset,
        int length,
        String message,
        List<String> suggestions,
        String ruleId,
        Severity severity) {

    /** How serious an issue is: natural-language checks warn, syntax errors. */
    public enum Severity {
        WARNING,
        ERROR
    }

    /** Convenience: a {@link Severity#WARNING} issue (the common case). */
    public ValidationIssue(int offset, int length, String message,
            List<String> suggestions, String ruleId) {
        this(offset, length, message, suggestions, ruleId, Severity.WARNING);
    }
}
