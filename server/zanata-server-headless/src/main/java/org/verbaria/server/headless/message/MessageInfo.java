package org.verbaria.server.headless.message;

import java.util.List;
import java.util.function.Function;

/**
 * The result of {@link MessageEvaluator#analyze(String)} for one message:
 * whether the pattern is valid, the list of argument labels it expects, and a
 * formatter that renders the message given (raw string) argument inputs.
 */
public final class MessageInfo {
    private final boolean valid;
    private final String error;
    private final List<String> argumentLabels;
    private final Function<List<String>, String> formatter;

    private MessageInfo(boolean valid, String error, List<String> labels,
            Function<List<String>, String> formatter) {
        this.valid = valid;
        this.error = error;
        this.argumentLabels = labels == null ? List.of() : List.copyOf(labels);
        this.formatter = formatter;
    }

    /** The pattern could not be parsed; {@code error} explains why. */
    public static MessageInfo invalid(String error) {
        return new MessageInfo(false, error, List.of(), null);
    }

    /** A valid pattern with the given argument labels and a renderer. */
    public static MessageInfo valid(List<String> argumentLabels,
            Function<List<String>, String> formatter) {
        return new MessageInfo(true, null, argumentLabels, formatter);
    }

    public boolean isValid() {
        return valid;
    }

    public String getError() {
        return error;
    }

    /** Number of arguments the message expects (0 = no arguments). */
    public int getArgumentCount() {
        return argumentLabels.size();
    }

    public List<String> getArgumentLabels() {
        return argumentLabels;
    }

    /**
     * Render the message with the given raw string inputs (one per argument,
     * in order). May throw {@link IllegalArgumentException} if the inputs don't
     * suit the pattern (e.g. a number argument given non-numeric text).
     */
    public String format(List<String> inputs) {
        if (!valid) {
            throw new IllegalStateException(error);
        }
        return formatter.apply(inputs);
    }
}
