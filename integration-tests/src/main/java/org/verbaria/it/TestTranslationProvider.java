package org.verbaria.it;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.verbaria.server.headless.service.ai.TranslationProvider;
import org.zanata.common.LocaleId;

/**
 * Deterministic AI provider for integration tests: every translation is
 * {@code AI(<source>)}. Lets the AI translate/save/approve flow be exercised
 * without a real model.
 */
@Component
public class TestTranslationProvider implements TranslationProvider {

    public static final String ID = "test";

    public static String expected(String source) {
        return "AI(" + (source == null ? "" : source) + ")";
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Test";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String translate(String source, LocaleId sourceLocale,
            LocaleId targetLocale, String context) {
        return expected(source);
    }

    @Override
    public List<String> translateBatch(List<TranslationRequest> requests,
            BatchProgress progress) {
        List<String> out = new ArrayList<>(requests.size());
        for (int i = 0; i < requests.size(); i++) {
            out.add(expected(requests.get(i).source()));
            if (progress != null) {
                progress.report(i + 1, requests.size());
            }
        }
        return out;
    }
}
