package org.zanata.spring.validation;

import java.util.List;

public record ValidationIssue(
        int offset,
        int length,
        String message,
        List<String> suggestions,
        String ruleId) {
}
