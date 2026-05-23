package org.zanata.util;

import java.util.function.Function;
import java.util.function.Supplier;
import org.openqa.selenium.support.ui.FluentWait;

/**
 * Java port of the former Kotlin FluentWait extensions.
 *
 * <pre>
 * FluentWaitExt.until(wait, "displayed", d -&gt; d.isDisplayed());
 * </pre>
 */
public final class FluentWaitExt {
    private FluentWaitExt() {}

    public static <T, V> V until(FluentWait<T> wait, String message,
            Function<? super T, V> isTruthy) {
        return until(wait, () -> message, isTruthy);
    }

    public static <T, V> V until(FluentWait<T> wait,
            Supplier<String> messageSupplier,
            Function<? super T, V> isTruthy) {
        wait.withMessage(messageSupplier);
        try {
            return wait.until(isTruthy::apply);
        } finally {
            wait.withMessage(() -> null);
        }
    }
}
