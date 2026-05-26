package org.zanata.spring.validation;

import java.util.List;
import java.util.Locale;

public interface LanguageValidator {

    List<ValidationIssue> validate(String text, Locale locale);
}
