package org.verbaria.server.headless.message;

import org.zanata.common.MessageEvaluateType;

/**
 * A component that understands one {@link MessageEvaluateType} (Java
 * MessageFormat, ICU4J MessageFormat, C/printf, ...). It parses a message
 * pattern and returns {@link MessageInfo} describing its arguments and how to
 * render it — used by the editor's "evaluate" preview. New message syntaxes can
 * be supported by adding a new implementation and registering it in
 * {@link MessageEvaluators}.
 */
public interface MessageEvaluator {

    /** The message type this evaluator handles. */
    MessageEvaluateType type();

    /**
     * Parse {@code pattern} and return its argument info plus a renderer.
     * Never throws — an unparseable pattern yields {@link MessageInfo#invalid}.
     */
    MessageInfo analyze(String pattern);
}
