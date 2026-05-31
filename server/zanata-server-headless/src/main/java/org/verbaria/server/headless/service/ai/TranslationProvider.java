package org.verbaria.server.headless.service.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zanata.common.LocaleId;

public interface TranslationProvider {

    String id();
    String displayName();
    boolean isAvailable();


    String translate(String source, LocaleId sourceLocale, LocaleId targetLocale, String context);

    /**
     * Default batch impl: chunks the requests, sends each chunk as a single
     * JSON-in/JSON-out prompt, runs chunks in parallel. Far fewer round-trips
     * than calling {@link #translate} N times. Providers may override for a
     * vendor-native batch API.
     */
    default List<String> translateBatch(List<TranslationRequest> requests) {
        return translateBatch(requests, null);
    }

    /**
     * Same as {@link #translateBatch(List)} but reports progress as chunks
     * complete. {@code progress} may be {@code null}.
     */
    default List<String> translateBatch(List<TranslationRequest> requests,
                                        BatchProgress progress) {
        if (requests.isEmpty()) return List.of();
        Logger log = LoggerFactory.getLogger(getClass());
        List<String> out = new ArrayList<>(Collections.nCopies(requests.size(), null));
        int chunkSize = batchChunkSize();
        List<int[]> chunks = new ArrayList<>();
        for (int from = 0; from < requests.size(); from += chunkSize) {
            chunks.add(new int[] {from, Math.min(requests.size(), from + chunkSize)});
        }
        int parallelism = Math.max(1, Math.min(4, chunks.size()));
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
        try {
            pool.submit(() -> chunks.parallelStream().forEach(range -> {
                int from = range[0], to = range[1];
                List<TranslationRequest> sub = requests.subList(from, to);
                List<String> chunkOut;
                try {
                    chunkOut = translateChunk(sub);
                } catch (Exception ex) {
                    log.warn("{} batch chunk failed, falling back to per-row: {}",
                            id(), ex.getMessage());
                    chunkOut = new ArrayList<>(Collections.nCopies(sub.size(), null));
                    for (int i = 0; i < sub.size(); i++) {
                        try {
                            TranslationRequest r = sub.get(i);
                            chunkOut.set(i, translate(r.source(), r.sourceLocale(),
                                    r.targetLocale(), r.context()));
                        } catch (Exception ignore) {
                        }
                    }
                }
                for (int i = 0; i < sub.size(); i++) {
                    out.set(from + i, chunkOut.get(i));
                }
                if (progress != null) {
                    int d = done.addAndGet(sub.size());
                    progress.report(d, requests.size());
                }
            })).join();
        } finally {
            pool.shutdown();
        }
        return out;
    }

    /**
     * Max items to pack into one batched API call. 10 keeps each call's
     * output well under our 8K-token reply ceiling even with HTML-rich
     * content translated to a verbose language; smaller chunks also mean
     * a single truncated/refused reply only loses 10 rows.
     */
    default int batchChunkSize() { return 10; }

    /**
     * Send a chunk to the model as a single JSON-in/JSON-out call. Default
     * builds a JSON-array prompt and parses a JSON-array reply. All requests
     * in a chunk must share the same source/target locale (which they do —
     * the caller groups by locale).
     */
    default List<String> translateChunk(List<TranslationRequest> chunk) {
        if (chunk.isEmpty()) return List.of();
        ObjectMapper json = new ObjectMapper();
        ArrayNode arr = json.createArrayNode();
        for (int i = 0; i < chunk.size(); i++) {
            TranslationRequest r = chunk.get(i);
            ObjectNode o = json.createObjectNode();
            o.put("i", i);
            if (r.context() != null && !r.context().isBlank()) o.put("context", r.context());
            o.put("text", r.source() == null ? "" : r.source());
            arr.add(o);
        }
        TranslationRequest first = chunk.get(0);
        String prompt;
        try {
            prompt = Prompts.buildBatchTranslate(
                    first.sourceLocale(), first.targetLocale(),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(arr));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build batch prompt", e);
        }
        String reply = translateRaw(prompt, first.sourceLocale(), first.targetLocale());
        return parseBatchReply(reply, chunk.size());
    }

    /**
     * Run an arbitrary prompt against the model and return its raw text reply.
     * Used by the default {@link #translateChunk} so providers don't need to
     * reimplement batching. Default delegates to {@link #translate}.
     */
    default String translateRaw(String prompt, LocaleId src, LocaleId tgt) {
        return translate(prompt, src, tgt, null);
    }

    /**
     * Parse the model's reply into translations, padding with {@code null}
     * where parsing fails. Tolerates:
     * <ul>
     *   <li>Stray prose / ```json fences around the array</li>
     *   <li>Objects ({@code {"text": "..."}}) instead of plain strings</li>
     *   <li><b>Truncated</b> arrays (max_tokens cutoff) — recovers every
     *       complete top-level element before the cutoff and leaves the
     *       rest as {@code null}.</li>
     * </ul>
     */
    static List<String> parseBatchReply(String reply, int size) {
        List<String> out = new ArrayList<>(Collections.nCopies(size, null));
        if (reply == null) return out;
        int lo = reply.indexOf('[');
        if (lo < 0) {
            LoggerFactory.getLogger(TranslationProvider.class)
                    .warn("Batch reply did not contain a JSON array: {}",
                            reply.length() > 200 ? reply.substring(0, 200) + "…" : reply);
            return out;
        }
        // Split the array body into top-level elements with a small
        // brace/bracket counter that respects string escapes. Anything that
        // parses as a JsonNode becomes an output; the rest are left null.
        List<String> elements = splitTopLevelArrayElements(reply, lo);
        ObjectMapper json = new ObjectMapper();
        for (int i = 0; i < elements.size() && i < size; i++) {
            String raw = elements.get(i).trim();
            if (raw.isEmpty()) continue;
            try {
                JsonNode n = json.readTree(raw);
                if (n == null || n.isNull()) continue;
                if (n.isTextual()) out.set(i, n.asText());
                else if (n.isObject() && n.has("text")) out.set(i, n.path("text").asText());
                else if (n.isObject() && n.has("translation")) out.set(i, n.path("translation").asText());
                else out.set(i, n.asText());
            } catch (Exception ignore) {
                // partial trailing element from max_tokens cutoff — leave null
            }
        }
        return out;
    }

    /**
     * Walk the array body from {@code reply[startBracket]} onwards, returning
     * each top-level element as a JSON-parseable substring. Stops at the
     * matching {@code ]} or at end-of-input (handles truncation). Respects
     * string quoting and escapes so commas inside strings don't split.
     */
    private static List<String> splitTopLevelArrayElements(String reply, int startBracket) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;          // brace + bracket depth INSIDE the outer array
        boolean inString = false;
        boolean escape = false;
        for (int i = startBracket + 1; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (escape) { cur.append(c); escape = false; continue; }
            if (inString) {
                cur.append(c);
                if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { cur.append(c); inString = true; continue; }
            if (c == '{' || c == '[') { depth++; cur.append(c); continue; }
            if (c == '}' || c == ']') {
                if (depth == 0) {
                    // matching ']' for the outer array → flush last element and stop
                    if (cur.toString().trim().length() > 0) out.add(cur.toString());
                    return out;
                }
                depth--;
                cur.append(c);
                continue;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        // ran off the end without seeing the closing ']' — truncated reply.
        // Keep any complete element we already flushed; drop the unfinished one
        // (it won't parse as JSON anyway).
        return out;
    }

    record TranslationRequest(String source, LocaleId sourceLocale,
                              LocaleId targetLocale, String context) {}

    /** Progress callback for {@link #translateBatch(List, BatchProgress)}. */
    @FunctionalInterface
    interface BatchProgress {
        void report(int done, int total);
    }
}
