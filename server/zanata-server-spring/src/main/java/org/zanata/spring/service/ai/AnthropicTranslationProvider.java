package org.zanata.spring.service.ai;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.zanata.common.LocaleId;

@Component
public class AnthropicTranslationProvider implements TranslationProvider, DisposableBean {

    public static final String KEY_API_KEY = "ai.anthropic.apikey";
    public static final String KEY_MODEL   = "ai.anthropic.model";

    public static final String DEFAULT_MODEL = "claude-sonnet-4-5";
    /**
     * Bound output so a batch chunk can't run away. 8K is enough for ~15
     * HTML-heavy strings translated to a verbose language (Russian/German),
     * still well below Sonnet 4.5's per-call limit.
     */
    private static final int MAX_TOKENS = 8192;
    /** Per-call hard ceiling; trims hung connections that otherwise sit forever in readHeaders. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);

    private final AiSettingsService settings;

    /**
     * Cached client + chat model. The Anthropic SDK's okhttp client owns a
     * dispatcher thread pool and connection pool; creating one per call leaks
     * threads. We rebuild only when the api-key or model changes.
     */
    private volatile Cached cached;

    private record Cached(String apiKey, String model,
                          AnthropicClient client, AnthropicChatModel chat) {}

    public AnthropicTranslationProvider(AiSettingsService settings) {
        this.settings = settings;
    }

    @Override public String id() { return "anthropic"; }
    @Override public String displayName() { return "Anthropic Claude"; }

    @Override
    public List<SettingField> settings() {
        return List.of(
                new SettingField(KEY_API_KEY, "API key",
                        "Anthropic secret key (sk-ant-…). Leave empty to disable.", "", true),
                new SettingField(KEY_MODEL, "Model",
                        "Claude model id, e.g. " + DEFAULT_MODEL, DEFAULT_MODEL, false));
    }

    @Override
    public boolean isAvailable() {
        return settings.getOrNull(KEY_API_KEY) != null;
    }

    @Override
    public String translate(String source, LocaleId src, LocaleId tgt, String context) {
        AnthropicChatModel chat = chatModel();
        var response = chat.call(new Prompt(Prompts.buildTranslate(source, src, tgt, context)));
        return response.getResult().getOutput().getText().trim();
    }

    private synchronized AnthropicChatModel chatModel() {
        String apiKey = settings.getOrNull(KEY_API_KEY);
        if (apiKey == null) throw new IllegalStateException("Anthropic API key not configured");
        String model = settings.get(KEY_MODEL, DEFAULT_MODEL);
        Cached c = cached;
        if (c != null && Objects.equals(c.apiKey(), apiKey) && Objects.equals(c.model(), model)) {
            return c.chat();
        }
        // Config changed — close the old client before swapping.
        if (c != null) closeQuietly(c.client());
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(CALL_TIMEOUT)
                .build();
        AnthropicChatOptions opts = AnthropicChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .maxTokens(MAX_TOKENS)
                .build();
        AnthropicChatModel chat = AnthropicChatModel.builder()
                .anthropicClient(client).options(opts).build();
        cached = new Cached(apiKey, model, client, chat);
        return chat;
    }

    @Override
    public void destroy() {
        Cached c = cached;
        if (c != null) closeQuietly(c.client());
        cached = null;
    }

    private static void closeQuietly(AnthropicClient client) {
        try { client.close(); } catch (Exception ignore) {}
    }
}
