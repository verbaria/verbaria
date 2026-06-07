package org.verbaria.server.headless.message;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.zanata.common.MessageEvaluateType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies parameter resolution (argument discovery + rendering) and
 * syntax-error detection for every message-evaluate type: Java MessageFormat,
 * ICU4J MessageFormat (numbered / named / plural / select) and C/printf
 * (sequential / positional / flags).
 */
class MessageEvaluatorsTest {

    private final MessageEvaluators registry = new MessageEvaluators(List.of(
            new JavaMessageFormatEvaluator(),
            new Icu4jMessageFormatEvaluator(),
            new PrintfMessageEvaluator()));

    private MessageInfo analyze(MessageEvaluateType type, String pattern) {
        MessageEvaluator e = registry.forType(type);
        assertThat(e).as("evaluator for " + type).isNotNull();
        return e.analyze(pattern, Locale.ENGLISH);
    }

    // --- registry ----------------------------------------------------------

    @Test
    void noneHasNoEvaluator() {
        assertThat(registry.forType(MessageEvaluateType.NONE)).isNull();
        assertThat(registry.forType(null)).isNull();
    }

    // --- Java MessageFormat -------------------------------------------------

    @Test
    void javaResolvesPositionalArgs() {
        MessageInfo info = analyze(
                MessageEvaluateType.JAVA_MESSAGE_FORMAT, "{0} files in {1}");
        assertThat(info.isValid()).isTrue();
        assertThat(info.getArgumentCount()).isEqualTo(2);
        assertThat(info.format(List.of("5", "/tmp")))
                .isEqualTo("5 files in /tmp");
    }

    @Test
    void javaRepeatedArgCountsOnce() {
        MessageInfo info = analyze(
                MessageEvaluateType.JAVA_MESSAGE_FORMAT, "{0} = {0}");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("x"))).isEqualTo("x = x");
    }

    @Test
    void javaNumberArgumentCoercedFromText() {
        MessageInfo info = analyze(
                MessageEvaluateType.JAVA_MESSAGE_FORMAT,
                "{0,number,integer} items");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("5"))).isEqualTo("5 items");
    }

    @Test
    void javaNumberFormatUsesTargetLocale() {
        MessageEvaluator e =
                registry.forType(MessageEvaluateType.JAVA_MESSAGE_FORMAT);
        assertThat(e.analyze("{0,number}", Locale.US).format(List.of("1234.5")))
                .isEqualTo("1,234.5");
        assertThat(e.analyze("{0,number}", Locale.GERMANY).format(List.of("1234.5")))
                .isEqualTo("1.234,5");
    }

    @Test
    void javaSyntaxErrorIsReported() {
        MessageInfo info = analyze(
                MessageEvaluateType.JAVA_MESSAGE_FORMAT, "{0} unbalanced {");
        assertThat(info.isValid()).isFalse();
        assertThat(info.getError()).isNotBlank();
    }

    // --- ICU4J MessageFormat ------------------------------------------------

    @Test
    void icuPluralChoosesFormByNumber() {
        MessageInfo info = analyze(MessageEvaluateType.ICU4J_MESSAGE_FORMAT,
                "{0, plural, one {# file} other {# files}}");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("1"))).isEqualTo("1 file");
        assertThat(info.format(List.of("3"))).isEqualTo("3 files");
    }

    @Test
    void icuSelectChoosesBranch() {
        MessageInfo info = analyze(MessageEvaluateType.ICU4J_MESSAGE_FORMAT,
                "{0, select, male {he} female {she} other {they}}");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("female"))).isEqualTo("she");
        assertThat(info.format(List.of("x"))).isEqualTo("they");
    }

    @Test
    void icuNamedArgsResolveInSortedOrder() {
        MessageInfo info = analyze(MessageEvaluateType.ICU4J_MESSAGE_FORMAT,
                "Hello {name}, you have {count} messages");
        assertThat(info.getArgumentCount()).isEqualTo(2);
        // names sorted -> {count}, {name}
        assertThat(info.getArgumentLabels())
                .containsExactly("{count}", "{name}");
        assertThat(info.format(List.of("3", "Bob")))
                .isEqualTo("Hello Bob, you have 3 messages");
    }

    @Test
    void icuPluralAndNumberUseGermanLocale() {
        MessageEvaluator e =
                registry.forType(MessageEvaluateType.ICU4J_MESSAGE_FORMAT);
        MessageInfo info = e.analyze(
                "{0, plural, one {# Datei} other {# Dateien}}", Locale.GERMANY);
        assertThat(info.format(List.of("1"))).isEqualTo("1 Datei");
        assertThat(info.format(List.of("1234"))).isEqualTo("1.234 Dateien");
    }

    @Test
    void icuSyntaxErrorIsReported() {
        MessageInfo info = analyze(MessageEvaluateType.ICU4J_MESSAGE_FORMAT,
                "{0, plural, one {x}");  // unterminated
        assertThat(info.isValid()).isFalse();
    }

    // --- C / printf ---------------------------------------------------------

    @Test
    void printfSequentialArgs() {
        MessageInfo info =
                analyze(MessageEvaluateType.C_PRINTF, "%s and %d");
        assertThat(info.getArgumentCount()).isEqualTo(2);
        assertThat(info.format(List.of("x", "7"))).isEqualTo("x and 7");
    }

    @Test
    void printfPositionalArgs() {
        MessageInfo info = analyze(MessageEvaluateType.C_PRINTF,
                "%2$s has %1$ld bytes");
        assertThat(info.getArgumentCount()).isEqualTo(2);
        // position 1 is the number (%ld), position 2 is the string (%s)
        assertThat(info.getArgumentLabels()).containsExactly("#1 %d", "#2 %s");
        assertThat(info.format(List.of("42", "file")))
                .isEqualTo("file has 42 bytes");
    }

    @Test
    void printfLiteralPercentIsNotAnArg() {
        MessageInfo info = analyze(MessageEvaluateType.C_PRINTF, "%d%% done");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("50"))).isEqualTo("50% done");
    }

    @Test
    void printfFloatWithPrecision() {
        MessageInfo info = analyze(MessageEvaluateType.C_PRINTF, "%.0f%%");
        assertThat(info.getArgumentCount()).isEqualTo(1);
        assertThat(info.format(List.of("5"))).isEqualTo("5%");
    }

    @Test
    void printfFloatUsesTargetLocale() {
        MessageEvaluator e = registry.forType(MessageEvaluateType.C_PRINTF);
        assertThat(e.analyze("%.1f", Locale.US).format(List.of("1.5")))
                .isEqualTo("1.5");
        assertThat(e.analyze("%.1f", Locale.GERMANY).format(List.of("1.5")))
                .isEqualTo("1,5");
    }
}
