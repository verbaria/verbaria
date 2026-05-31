package org.zanata.spring.message;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.zanata.common.MessageEvaluateType;

/**
 * Evaluator for C / gettext-PO printf-style format strings, e.g.
 * {@code "%d files in %s"} or positional {@code "%1$s = %2$d"}. The pattern is
 * normalised to a {@link String#format} compatible form (length modifiers such
 * as {@code %ld} are dropped; {@code %i}/{@code %u} map to {@code %d}), and
 * arguments are coerced to the type each conversion needs.
 */
@Component
public class PrintfMessageEvaluator implements MessageEvaluator {

    // %[index$][flags][width][.precision][length]conversion
    private static final Pattern SPEC = Pattern.compile(
            "%(?:(\\d+)\\$)?([-+ 0#]*)(\\d+|\\*)?(\\.(?:\\d+|\\*))?"
            + "(?:hh|h|ll|l|L|q|j|z|t)?([diouxXeEfFgGaAcspn%])");

    @Override
    public MessageEvaluateType type() {
        return MessageEvaluateType.C_PRINTF;
    }

    @Override
    public MessageInfo analyze(String pattern) {
        final String p = pattern == null ? "" : pattern;
        StringBuilder javaPattern = new StringBuilder();
        // 1-based argument position -> its conversion char. Sequential specs
        // get the next position; explicit %N$ specs use that position. Every
        // emitted Java spec is positional so the arg array stays unambiguous.
        TreeMap<Integer, Character> convByPos = new TreeMap<>();
        int seq = 0;
        Matcher m = SPEC.matcher(p);
        int last = 0;
        while (m.find()) {
            javaPattern.append(p, last, m.start());
            last = m.end();
            char conv = m.group(5).charAt(0);
            if (conv == '%') {
                javaPattern.append("%%");
                continue;
            }
            int pos = m.group(1) != null
                    ? Integer.parseInt(m.group(1)) : ++seq;
            convByPos.put(pos, conv);
            String flags = m.group(2) == null ? "" : m.group(2);
            String width = m.group(3);
            String prec = m.group(4);
            javaPattern.append('%').append(pos).append('$').append(flags);
            if (width != null && !width.contains("*")) javaPattern.append(width);
            if (prec != null && !prec.contains("*")) javaPattern.append(prec);
            javaPattern.append(mapConversion(conv));
        }
        javaPattern.append(p, last, p.length());
        final String fmt = javaPattern.toString();

        final int count = convByPos.isEmpty() ? 0 : convByPos.lastKey();
        List<String> labels = new ArrayList<>(count);
        for (int pos = 1; pos <= count; pos++) {
            char conv = convByPos.getOrDefault(pos, 's');
            labels.add("#" + pos + " %" + conv);
        }
        return MessageInfo.valid(labels, inputs -> {
            Object[] args = new Object[count];
            for (int pos = 1; pos <= count; pos++) {
                char conv = convByPos.getOrDefault(pos, 's');
                String in = pos - 1 < inputs.size() ? inputs.get(pos - 1) : "";
                args[pos - 1] = coerce(conv, in);
            }
            try {
                return String.format(fmt, args);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(e.getMessage(), e);
            }
        });
    }

    /** Map C conversions to the nearest Java {@link String#format} conversion. */
    private static char mapConversion(char c) {
        return switch (c) {
            case 'i', 'u' -> 'd';   // Java has no unsigned; closest is %d
            case 'p', 'n' -> 's';   // pointer / count -> render as text
            default -> c;
        };
    }

    private static Object coerce(char conv, String in) {
        String t = in == null ? "" : in.trim();
        switch (conv) {
            case 'd': case 'i': case 'o': case 'u': case 'x': case 'X':
                return t.isEmpty() ? 0L : Long.valueOf(t);
            case 'e': case 'E': case 'f': case 'F':
            case 'g': case 'G': case 'a': case 'A':
                return t.isEmpty() ? 0d : Double.valueOf(t);
            case 'c':
                return t.isEmpty() ? ' ' : t.charAt(0);
            default:
                return in == null ? "" : in;
        }
    }
}
