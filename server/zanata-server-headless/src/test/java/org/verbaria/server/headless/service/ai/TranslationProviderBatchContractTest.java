package org.verbaria.server.headless.service.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.zanata.common.LocaleId;

/**
 * The batch path ({@link TranslationProvider#translateChunk}) sends a
 * JSON-in/JSON-out prompt through {@link TranslationProvider#translateRaw}. The
 * interface default of {@code translateRaw} delegates to {@code translate},
 * which wraps its argument in a "translate this text" prompt — so a provider
 * whose {@code translate} wraps MUST override {@code translateRaw} to run the
 * prompt verbatim, otherwise the batch prompt is double-wrapped, the reply is
 * not a JSON array, and every batch translation comes back null
 * ("Nothing to translate").
 */
class TranslationProviderBatchContractTest {

    @Test
    void realProvidersOverrideTranslateRaw() {
        for (Class<?> provider : List.of(AnthropicTranslationProvider.class,
                OpenAiTranslationProvider.class)) {
            assertThatNoException()
                    .as("%s must declare translateRaw (not inherit the wrapping "
                            + "default) so batch prompts aren't double-wrapped",
                            provider.getSimpleName())
                    .isThrownBy(() -> {
                        Method m = provider.getDeclaredMethod("translateRaw",
                                String.class, LocaleId.class, LocaleId.class);
                        assertThat(m).isNotNull();
                    });
        }
    }
}
