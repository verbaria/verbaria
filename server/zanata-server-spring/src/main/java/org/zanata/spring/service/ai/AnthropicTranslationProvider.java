package org.zanata.spring.service.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.zanata.common.LocaleId;

@org.springframework.stereotype.Component
public class AnthropicTranslationProvider implements TranslationProvider, DisposableBean {

    public static final String KEY_API_KEY    = "ai.anthropic.apikey";
    /** OAuth bearer token (Claude subscription / OAuth flow), sent as Authorization: Bearer. */
    public static final String KEY_AUTH_TOKEN = "ai.anthropic.authtoken";
    /** Which credential to use: {@link #MODE_API_KEY} or {@link #MODE_OAUTH}. */
    public static final String KEY_AUTH_MODE  = "ai.anthropic.authmode";
    public static final String KEY_MODEL      = "ai.anthropic.model";

    /**
     * When {@code true}, translate via the bundled {@code claude-translate.sh}
     * (the official Claude Code CLI) instead of the HTTP API. This lets a
     * Max/Pro subscription OAuth token actually serve requests — the raw
     * Messages API refuses subscription tokens, but the first-party CLI
     * accepts them, with no separate API billing.
     */
    public static final String KEY_NATIVE_CLIENT = "ai.anthropic.nativeclient";
    /** Path to the {@code claude} binary the native client runs (default {@code claude}). */
    public static final String KEY_CLI_BINARY    = "ai.anthropic.clibinary";

    public static final String MODE_API_KEY = "apikey";
    public static final String MODE_OAUTH   = "oauth";

    public static final String DEFAULT_CLI_BINARY = "claude";
    /** Classpath location of the bundled native-client shim. */
    private static final String SCRIPT_RESOURCE = "ai/claude-translate.sh";
    /** Hard ceiling for a native CLI call (the CLI spins up a session). */
    private static final Duration CLI_TIMEOUT = Duration.ofSeconds(180);

    public static final String DEFAULT_MODEL = "claude-sonnet-4-6";
    /**
     * Bound output so a batch chunk can't run away. 8K is enough for ~15
     * HTML-heavy strings translated to a verbose language (Russian/German),
     * still well below Sonnet 4.5's per-call limit.
     */
    private static final int MAX_TOKENS = 8192;
    /** Per-call hard ceiling; trims hung connections that otherwise sit forever in readHeaders. */
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(120);
    /** Automatic retries on 429/5xx (exponential backoff, honours retry-after). */
    private static final int MAX_RETRIES = 5;
    /** Beta flag that marks a request as an OAuth (subscription) session. */
    private static final String OAUTH_BETA = "oauth-2025-04-20";

    private final AiSettingsService settings;

    /**
     * Cached client + chat model. The Anthropic SDK's okhttp client owns a
     * dispatcher thread pool and connection pool; creating one per call leaks
     * threads. We rebuild only when the auth mode, credential or model changes.
     */
    private volatile Cached cached;

    private record Cached(String mode, String credential, String model,
                          AnthropicClient client, AnthropicChatModel chat) {}

    public AnthropicTranslationProvider(AiSettingsService settings) {
        this.settings = settings;
    }

    @Override public String id() { return "anthropic"; }
    @Override public String displayName() { return "Anthropic Claude"; }

    /** Active auth mode, defaulting to API-key. */
    private String authMode() {
        String m = settings.get(KEY_AUTH_MODE, MODE_API_KEY);
        return MODE_OAUTH.equals(m) ? MODE_OAUTH : MODE_API_KEY;
    }

    /** The credential for the active mode, or {@code null} if not configured. */
    private String activeCredential() {
        return settings.getOrNull(
                MODE_OAUTH.equals(authMode()) ? KEY_AUTH_TOKEN : KEY_API_KEY);
    }

    /** Whether to translate through the bundled Claude Code CLI shim. */
    private boolean nativeClient() {
        return Boolean.parseBoolean(settings.get(KEY_NATIVE_CLIENT, "false"));
    }

    @Override
    public boolean isAvailable() {
        // Native client authenticates via CLAUDE_CODE_OAUTH_TOKEN (the OAuth
        // token field) or the server's own `claude` login. Treat it as
        // available when native mode is on and a token is set; otherwise the
        // normal API credential check applies.
        if (nativeClient()) {
            return settings.getOrNull(KEY_AUTH_TOKEN) != null;
        }
        return activeCredential() != null;
    }

    @Override
    public com.vaadin.flow.component.Component createSettingsPage() {
        return new AnthropicSettingsPage(settings, displayName());
    }

    @Override
    public String translate(String source, LocaleId src, LocaleId tgt, String context) {
        String prompt = Prompts.buildTranslate(source, src, tgt, context);
        if (nativeClient()) {
            return runNative(prompt).trim();
        }
        AnthropicChatModel chat = chatModel();
        var response = chat.call(new Prompt(prompt));
        return response.getResult().getOutput().getText().trim();
    }

    // ------------------------------------------------------------------
    // Native client (bundled claude-translate.sh → Claude Code CLI)
    // ------------------------------------------------------------------

    /** Extracted script path, lazily materialised from the classpath. */
    private volatile Path scriptPath;

    /**
     * Run a prompt through the bundled shim. The prompt goes in on stdin
     * (safe for arbitrary multi-line content); the subscription OAuth token
     * is passed via {@code CLAUDE_CODE_OAUTH_TOKEN} in the child environment.
     */
    private String runNative(String prompt) {
        try {
            Path script = ensureScriptExtracted();
            String binary = settings.get(KEY_CLI_BINARY, DEFAULT_CLI_BINARY);
            String model = settings.get(KEY_MODEL, DEFAULT_MODEL);

            ProcessBuilder pb = new ProcessBuilder(
                    "/usr/bin/env", "sh", script.toString(), model);
            pb.environment().put("CLAUDE_BIN", binary);
            String token = settings.getOrNull(KEY_AUTH_TOKEN);
            if (token != null) {
                pb.environment().put("CLAUDE_CODE_OAUTH_TOKEN", token);
            }
            pb.redirectErrorStream(false);
            Process proc = pb.start();

            // Feed the prompt on stdin, then read stdout/stderr fully.
            try (OutputStream stdin = proc.getOutputStream()) {
                stdin.write(prompt.getBytes(StandardCharsets.UTF_8));
            }
            byte[] out = proc.getInputStream().readAllBytes();
            byte[] err = proc.getErrorStream().readAllBytes();

            if (!proc.waitFor(CLI_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                throw new IllegalStateException("claude CLI timed out after "
                        + CLI_TIMEOUT.toSeconds() + "s");
            }
            int code = proc.exitValue();
            if (code != 0) {
                throw new IllegalStateException("claude CLI exited " + code + ": "
                        + new String(err, StandardCharsets.UTF_8).trim());
            }
            return new String(out, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Native claude translation failed: "
                    + e.getMessage(), e);
        }
    }

    /**
     * Copy the bundled shim out of the jar to a private temp file once, mark
     * it executable, and reuse it for the JVM's lifetime.
     */
    private Path ensureScriptExtracted() throws IOException {
        Path p = scriptPath;
        if (p != null && Files.exists(p)) return p;
        synchronized (this) {
            if (scriptPath != null && Files.exists(scriptPath)) return scriptPath;
            Path tmp = Files.createTempFile("claude-translate-", ".sh");
            try (InputStream in = new ClassPathResource(SCRIPT_RESOURCE).getInputStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.setPosixFilePermissions(tmp,
                        PosixFilePermissions.fromString("rwx------"));
            } catch (UnsupportedOperationException ignore) {
                tmp.toFile().setExecutable(true, true);
            }
            tmp.toFile().deleteOnExit();
            scriptPath = tmp;
            return tmp;
        }
    }

    private synchronized AnthropicChatModel chatModel() {
        String mode = authMode();
        String credential = activeCredential();
        if (credential == null) {
            throw new IllegalStateException("Anthropic credentials not configured");
        }
        String model = settings.get(KEY_MODEL, DEFAULT_MODEL);
        Cached c = cached;
        if (c != null && Objects.equals(c.mode(), mode)
                && Objects.equals(c.credential(), credential)
                && Objects.equals(c.model(), model)) {
            return c.chat();
        }
        // Config changed — close the old client before swapping.
        if (c != null) closeQuietly(c.client());
        AnthropicOkHttpClient.Builder builder = AnthropicOkHttpClient.builder()
                .timeout(CALL_TIMEOUT)
                // Anthropic returns 429 rate_limit_error under bursty load
                // (the batch path fires several chunks in parallel, and
                // OAuth/subscription credentials have tighter limits than a
                // pay-as-you-go API key). The SDK retries 429/5xx with
                // exponential backoff honouring the retry-after header; bump
                // the default (~2) so transient limits self-heal instead of
                // surfacing as a failed translation.
                .maxRetries(MAX_RETRIES);
        // API key → x-api-key header; OAuth → Authorization: Bearer.
        if (MODE_OAUTH.equals(mode)) {
            builder.authToken(credential)
                    // Mark the request as an OAuth/first-party session.
                    // Without this beta flag Anthropic doesn't recognise the
                    // subscription token and applies anonymous (low) limits —
                    // which is why a Claude Max token still 429s. With it, the
                    // call draws on the subscription's own rate-limit pool.
                    .putHeader("anthropic-beta", OAUTH_BETA);
        } else {
            builder.apiKey(credential);
        }
        AnthropicClient client = builder.build();
        AnthropicChatOptions opts = AnthropicChatOptions.builder()
                .model(model)
                .temperature(0.2)
                .maxTokens(MAX_TOKENS)
                .build();
        AnthropicChatModel chat = AnthropicChatModel.builder()
                .anthropicClient(client).options(opts).build();
        cached = new Cached(mode, credential, model, client, chat);
        return chat;
    }

    @Override
    public void destroy() {
        Cached c = cached;
        if (c != null) closeQuietly(c.client());
        cached = null;
        Path p = scriptPath;
        if (p != null) {
            try { Files.deleteIfExists(p); } catch (IOException ignore) {}
            scriptPath = null;
        }
    }

    private static void closeQuietly(AnthropicClient client) {
        try { client.close(); } catch (Exception ignore) {}
    }
}
