package org.verbaria.server.headless.validation;

import java.util.List;
import java.util.Locale;

public interface LanguageValidator {

    List<ValidationIssue> validate(String text, Locale locale);
}
