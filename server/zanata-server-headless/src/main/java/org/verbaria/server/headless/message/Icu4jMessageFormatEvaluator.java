package org.verbaria.server.headless.message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.zanata.common.MessageEvaluateType;

import com.ibm.icu.text.MessageFormat;

/**
 * Evaluator for ICU4J {@code com.ibm.icu.text.MessageFormat} patterns,
 * including plural/select/gender, e.g.
 * {@code "{count, plural, one{# file} other{# files}}"} and named arguments
 * {@code "Hello {name}"}. Arguments may be numbered or named; numbers are
 * coerced from numeric input so plural/select work.
 */
@Component
public class Icu4jMessageFormatEvaluator implements MessageEvaluator {

    @Override
    public MessageEvaluateType type() {
        return MessageEvaluateType.ICU4J_MESSAGE_FORMAT;
    }

    @Override
    public MessageInfo analyze(String pattern, Locale locale) {
        final String p = pattern == null ? "" : pattern;
        final Locale loc = locale == null ? Locale.ROOT : locale;
        final MessageFormat parsed;
        try {
            parsed = new MessageFormat(p, loc);
        } catch (IllegalArgumentException e) {
            return MessageInfo.invalid(e.getMessage());
        }
        if (parsed.usesNamedArguments()) {
            // Sorted so the argument fields/inputs have a stable order.
            List<String> names = new ArrayList<>(parsed.getArgumentNames());
            names.sort(null);
            List<String> labels = new ArrayList<>(names.size());
            for (String n : names) {
                labels.add("{" + n + "}");
            }
            return MessageInfo.valid(labels, inputs -> {
                Map<String, Object> args = new HashMap<>();
                for (int i = 0; i < names.size(); i++) {
                    String in = i < inputs.size() ? inputs.get(i) : "";
                    args.put(names.get(i), coerce(in));
                }
                return parsed.format(args);
            });
        }
        int count = parsed.getFormatsByArgumentIndex().length;
        List<String> labels = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            labels.add("{" + i + "}");
        }
        return MessageInfo.valid(labels, inputs -> {
            Object[] args = new Object[count];
            for (int i = 0; i < count; i++) {
                String in = i < inputs.size() ? inputs.get(i) : "";
                args[i] = coerce(in);
            }
            return parsed.format(args);
        });
    }

    /** Numbers (for plural/select) when the input is numeric, else text. */
    private static Object coerce(String in) {
        if (in == null) return "";
        String t = in.trim();
        if (t.isEmpty()) return "";
        try {
            return Long.valueOf(t);
        } catch (NumberFormatException ignore) {
            // fall through
        }
        try {
            return Double.valueOf(t);
        } catch (NumberFormatException ignore) {
            return in;
        }
    }
}
