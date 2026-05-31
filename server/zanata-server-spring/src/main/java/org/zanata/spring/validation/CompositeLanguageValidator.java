package org.zanata.spring.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs several {@link LanguageValidator}s and merges their issues — e.g. the
 * natural-language checker (warnings) plus the message-format syntax checker
 * (errors). Built per editor row, so it is not a singleton bean.
 */
public class CompositeLanguageValidator implements LanguageValidator {

    private final List<LanguageValidator> validators;

    public CompositeLanguageValidator(List<LanguageValidator> validators) {
        this.validators = List.copyOf(validators);
    }

    @Override
    public List<ValidationIssue> validate(String text, Locale locale) {
        List<ValidationIssue> all = new ArrayList<>();
        for (LanguageValidator validator : validators) {
            List<ValidationIssue> issues = validator.validate(text, locale);
            if (issues != null) {
                all.addAll(issues);
            }
        }
        return all;
    }
}
