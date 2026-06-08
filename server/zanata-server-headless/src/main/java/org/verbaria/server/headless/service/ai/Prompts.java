package org.verbaria.server.headless.service.ai;

import org.zanata.common.LocaleId;

final class Prompts {
    private Prompts() {}

    static String buildTranslate(String source, LocaleId src, LocaleId tgt,
            String context, String guidance) {
        StringBuilder sb = new StringBuilder();
        sb.append("Translate the following text from ").append(src.getId())
                .append(" to ").append(tgt.getId()).append(".\n");
        if (context != null && !context.isBlank()) {
            sb.append("UI key (do NOT translate): ").append(context).append('\n');
        }
        if (guidance != null && !guidance.isBlank()) {
            sb.append(guidance.strip()).append('\n');
        }
        sb.append("Preserve {0}/{1}/HTML markup verbatim. ")
                .append("Reply with ONLY the translation, no quotes, no commentary.\n\n")
                .append(source);
        return sb.toString();
    }

    /**
     * Batched JSON-in / JSON-out prompt. {@code itemsJson} is a JSON array of
     * {@code {i, context?, text}}; the model must reply with a JSON array of
     * the same length whose i-th element is the translation of the i-th input.
     */
    static String buildBatchTranslate(LocaleId src, LocaleId tgt, String itemsJson,
            String guidance) {
        return "You are a professional software-localisation translator. "
                + "Translate every item from " + src.getId()
                + " to " + tgt.getId() + ".\n"
                + (guidance != null && !guidance.isBlank()
                        ? guidance.strip() + "\n" : "")
                + "Rules:\n"
                + "- Preserve placeholders like {0}/{1}/%s/%d, HTML tags and Markdown markup verbatim.\n"
                + "- Do NOT translate the value of the optional \"context\" field — only \"text\".\n"
                + "- Keep leading/trailing whitespace and line breaks as in the source \"text\".\n"
                + "- Output format: a JSON array of PLAIN STRINGS, no objects, no keys, no \"i\"/\"context\"/\"text\" wrappers.\n"
                + "- Array length and order MUST match the input exactly. Element i is the translation of input i.\n"
                + "- Reply with ONLY the JSON array. No commentary, no prose, no markdown, no code fence.\n\n"
                + "Example output (for 3 inputs):\n"
                + "[\"первый перевод\", \"second translation\", \"třetí překlad\"]\n\n"
                + "Input:\n" + itemsJson;
    }
}
