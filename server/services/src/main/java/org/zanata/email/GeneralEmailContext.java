package org.zanata.email;

/**
 * Top-level configuration shared across all generated emails (e.g. footer).
 *
 * @param serverURL the configured URL of Zanata
 * @param fromEmail the configured From email used by Zanata (ignored by email
 *                  templates which provide a more specific address)
 */
public record GeneralEmailContext(String serverURL, String fromEmail) {
}
