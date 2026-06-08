package org.verbaria.server.headless.service.ai;

import java.time.Duration;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.zanata.common.LocaleId;

@Component
public class OpenAiTranslationProvider implements TranslationProvider, DisposableBean {

    public static final String KEY_API_KEY = "ai.openai.apikey";
    public static final String KEY_MODEL   = "ai.openai.model";
    public static final String KEY_BASE    = "ai.openai.base";

    public static final String DEFAULT_BASE  = "https://api.openai.com/v1";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";

    private static final int MAX_TOKENS = 8192;
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);

    private final AiSettingsService settings;

    private volatile Cached cached;

    private record Cached(String apiKey, String baseUrl, String model,
                          OpenAIClient client, OpenAiChatModel chat) {}

    public OpenAiTranslationProvider(AiSettingsService settings) {
        this.settings = settings;
    }

    @Override public String id() { return "openai"; }
    @Override public String displayName() { return "OpenAI"; }

    @Override
    public boolean isAvailable() {
        return settings.getOrNull(KEY_API_KEY) != null;
    }

    @Override
    public String translate(String source, LocaleId src, LocaleId tgt,
            String context, String guidance) {
        OpenAiChatModel chat = chatModel();
        var response = chat.call(new Prompt(
                Prompts.buildTranslate(source, src, tgt, context, guidance)));
        return response.getResult().getOutput().getText().trim();
    }

    private synchronized OpenAiChatModel chatModel() {
        String apiKey = settings.getOrNull(KEY_API_KEY);
        if (apiKey == null) throw new IllegalStateException("OpenAI API key not configured");
        String base  = settings.get(KEY_BASE, DEFAULT_BASE);
        String model = settings.get(KEY_MODEL, DEFAULT_MODEL);
        Cached c = cached;
        if (c != null && Objects.equals(c.apiKey(), apiKey)
                && Objects.equals(c.baseUrl(), base)
                && Objects.equals(c.model(), model)) {
            return c.chat();
        }
        if (c != null) closeQuietly(c.client());
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(apiKey).baseUrl(base)
                .timeout(CALL_TIMEOUT)
                .build();
        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .maxTokens(MAX_TOKENS)
                .build();
        OpenAiChatModel chat = OpenAiChatModel.builder()
                .openAiClient(client).options(opts).build();
        cached = new Cached(apiKey, base, model, client, chat);
        return chat;
    }

    @Override
    public void destroy() {
        Cached c = cached;
        if (c != null) closeQuietly(c.client());
        cached = null;
    }

    private static void closeQuietly(OpenAIClient client) {
        try { client.close(); } catch (Exception ignore) {}
    }
}
