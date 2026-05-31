package org.zanata.spring.message;

import java.text.DateFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Component;
import org.zanata.common.MessageEvaluateType;

/**
 * Evaluator for {@code java.text.MessageFormat} patterns, e.g.
 * {@code "{0} files copied to {1}"} or {@code "{0,number,integer} items"}.
 * Argument count and types come from
 * {@link MessageFormat#getFormatsByArgumentIndex()}.
 */
@Component
public class JavaMessageFormatEvaluator implements MessageEvaluator {

    @Override
    public MessageEvaluateType type() {
        return MessageEvaluateType.JAVA_MESSAGE_FORMAT;
    }

    @Override
    public MessageInfo analyze(String pattern) {
        final String p = pattern == null ? "" : pattern;
        final Format[] formats;
        try {
            formats = new MessageFormat(p).getFormatsByArgumentIndex();
        } catch (IllegalArgumentException e) {
            return MessageInfo.invalid(e.getMessage());
        }
        List<String> labels = new ArrayList<>(formats.length);
        for (int i = 0; i < formats.length; i++) {
            labels.add("{" + i + "} (" + kind(formats[i]) + ")");
        }
        return MessageInfo.valid(labels, inputs -> {
            MessageFormat fmt = new MessageFormat(p);
            Format[] fs = fmt.getFormatsByArgumentIndex();
            Object[] args = new Object[fs.length];
            for (int i = 0; i < fs.length; i++) {
                String in = i < inputs.size() ? inputs.get(i) : "";
                args[i] = coerce(fs[i], in);
            }
            return fmt.format(args);
        });
    }

    private static String kind(Format f) {
        if (f instanceof NumberFormat) return "number";
        if (f instanceof DateFormat) return "date";
        return "text";
    }

    private static Object coerce(Format f, String in) {
        String t = in == null ? "" : in.trim();
        if (f instanceof NumberFormat) {
            if (t.isEmpty()) return 0L;
            try {
                return Long.valueOf(t);
            } catch (NumberFormatException e) {
                return Double.valueOf(t);
            }
        }
        if (f instanceof DateFormat) {
            try {
                return ((DateFormat) f).parse(t);
            } catch (Exception e) {
                return new Date();
            }
        }
        return in == null ? "" : in;
    }
}
