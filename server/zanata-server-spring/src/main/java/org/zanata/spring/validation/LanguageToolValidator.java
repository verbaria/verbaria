package org.zanata.spring.validation;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.languagetool.JLanguageTool;
import org.languagetool.Language;
import org.languagetool.Languages;
import org.languagetool.rules.RuleMatch;
import org.springframework.stereotype.Component;

@Component
public class LanguageToolValidator implements LanguageValidator {

    static {
        System.setProperty("javax.xml.parsers.SAXParserFactory",
                "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
        System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
                "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
    }

    private final Map<String, JLanguageTool> tools = new ConcurrentHashMap<>();

    @Override
    public List<ValidationIssue> validate(String text, Locale locale) {
        if (text == null || text.isEmpty() || locale == null) {
            return List.of();
        }
        JLanguageTool lt = toolFor(locale);
        if (lt == null) {
            return List.of();
        }
        try {
            return lt.check(text).stream()
                    .map(LanguageToolValidator::toIssue)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private JLanguageTool toolFor(Locale locale) {
        String key = locale.toLanguageTag();
        JLanguageTool cached = tools.get(key);
        if (cached != null) {
            return cached;
        }
        Language lang = null;
        if (Languages.isLanguageSupported(key)) {
            lang = Languages.getLanguageForShortCode(key);
        } else if (Languages.isLanguageSupported(locale.getLanguage())) {
            lang = Languages.getLanguageForShortCode(locale.getLanguage());
        }
        if (lang == null) {
            return null;
        }
        Language variant = lang.getDefaultLanguageVariant();
        if (variant != null) {
            lang = variant;
        }
        JLanguageTool created = new JLanguageTool(lang);
        JLanguageTool existing = tools.putIfAbsent(key, created);
        return existing != null ? existing : created;
    }

    private static ValidationIssue toIssue(RuleMatch m) {
        return new ValidationIssue(
                m.getFromPos(),
                m.getToPos() - m.getFromPos(),
                m.getMessage(),
                m.getSuggestedReplacements(),
                m.getRule().getId());
    }
}
