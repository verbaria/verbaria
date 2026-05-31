package org.verbaria.server.headless.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class LanguageToolValidatorTest {

    private final LanguageToolValidator validator = new LanguageToolValidator();

    @Test
    void nullText_returnsEmpty() {
        assertThat(validator.validate(null, Locale.ENGLISH)).isEmpty();
    }

    @Test
    void emptyText_returnsEmpty() {
        assertThat(validator.validate("", Locale.ENGLISH)).isEmpty();
    }

    @Test
    void nullLocale_returnsEmpty() {
        assertThat(validator.validate("Some text", null)).isEmpty();
    }

    @Test
    void unsupportedLocale_returnsEmpty() {
        Locale klingon = Locale.forLanguageTag("tlh");
        assertThat(validator.validate("nuqneH", klingon)).isEmpty();
    }

    @Test
    void englishMisspelling_returnsIssue() {
        List<ValidationIssue> issues =
                validator.validate("This is teh wrong word.", Locale.ENGLISH);
        assertThat(issues).isNotEmpty();
        assertThat(issues).anySatisfy(i -> {
            assertThat(i.offset()).isGreaterThanOrEqualTo(0);
            assertThat(i.length()).isGreaterThan(0);
            assertThat(i.message()).isNotBlank();
        });
    }

    @Test
    void englishCorrect_returnsFewOrNoIssues() {
        List<ValidationIssue> issues =
                validator.validate("This sentence is correct.", Locale.ENGLISH);
        assertThat(issues).isEmpty();
    }

    @Test
    void issueIncludesSuggestions() {
        List<ValidationIssue> issues =
                validator.validate("This is teh wrong word.", Locale.ENGLISH);
        assertThat(issues).anySatisfy(i ->
                assertThat(i.suggestions()).contains("the"));
    }

    @Test
    void issueIncludesRuleId() {
        List<ValidationIssue> issues =
                validator.validate("This is teh wrong word.", Locale.ENGLISH);
        assertThat(issues).allSatisfy(i ->
                assertThat(i.ruleId()).isNotBlank());
    }

    @Test
    void repeatedLocale_reusesCachedTool() {
        validator.validate("First check.", Locale.ENGLISH);
        // Second call should hit the cache — no exception, consistent result.
        List<ValidationIssue> second =
                validator.validate("First check.", Locale.ENGLISH);
        assertThat(second).isEmpty();
    }

    @Test
    void germanText_isValidated() {
        List<ValidationIssue> issues =
                validator.validate("Das ist ein Test mit einemm Fehler.", Locale.GERMAN);
        assertThat(issues).isNotEmpty();
    }

    @Test
    void fullLocaleTag_fallsBackToLanguageCode() {
        // en-AU pack isn't shipped; the validator should fall back to "en".
        List<ValidationIssue> issues = validator.validate(
                "This is teh wrong word.", Locale.forLanguageTag("en-AU"));
        assertThat(issues).isNotEmpty();
    }
}
