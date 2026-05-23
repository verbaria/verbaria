package org.zanata.hibernate.search;

/**
 * Forwarder stub. Real impl in zanata-model/src/main/java-disabled/.
 * Used by TranslationMemoryServiceImpl during search; until the Hibernate Search
 * 7 port lands, the methods return harmless defaults.
 */
public final class TextContainerAnalyzerDiscriminator {

    public static final String NAME = "textContainerDiscriminator";

    private TextContainerAnalyzerDiscriminator() {}

    /** Stub. Returns the locale id verbatim (no per-locale analyzer mapping). */
    public static String getAnalyzerDefinitionName(String localeId) {
        return localeId == null ? "" : localeId;
    }
}
