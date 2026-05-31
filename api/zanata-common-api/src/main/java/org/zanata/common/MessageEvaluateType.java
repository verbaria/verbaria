package org.zanata.common;

/**
 * How a project's message strings should be parsed/evaluated for the editor's
 * "evaluate" preview. Set per project in the project settings.
 * <ul>
 *   <li>{@link #NONE} — plain text, no evaluation (default);</li>
 *   <li>{@link #JAVA_MESSAGE_FORMAT} — {@code java.text.MessageFormat}
 *       patterns (e.g. {@code "{0} files in {1}"});</li>
 *   <li>{@link #ICU4J_MESSAGE_FORMAT} — ICU4J
 *       {@code com.ibm.icu.text.MessageFormat} patterns (plural/select/gender
 *       aware);</li>
 *   <li>{@link #C_PRINTF} — C / gettext-PO printf-style format strings
 *       ({@code %s}, {@code %d}, {@code %f}, positional {@code %1$s}, ...).</li>
 * </ul>
 * Extra types can be added later (e.g. Angular/Vue i18n); each gets its own
 * evaluator component on the server.
 */
public enum MessageEvaluateType {
    NONE,
    JAVA_MESSAGE_FORMAT,
    ICU4J_MESSAGE_FORMAT,
    C_PRINTF
}
