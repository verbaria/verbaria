/*
 * Labels used in lucene indexes. Restored from java-disabled while Hibernate
 * Search bridges are being migrated.
 */
package org.zanata.hibernate.search;

public interface IndexFieldLabels {
    String PROJECT_ID_FIELD = "projectId";
    String ENTITY_STATUS = "status";
    String DOCUMENT_ID_FIELD = "documentId";
    String LOCALE_ID_FIELD = "locale";
    String CONTENT_STATE_FIELD = "state";
    String LAST_CHANGED_FIELD = "lastChanged";

    String TF_CONTENT = "textFlow.content-nocase";
    String CONTENT = "content-nocase";
    String TF_RES_ID = "textFlow.resId";
    String TF_ID = "textFlow.id";
    String TF_CONTENT_HASH = "textFlow.contentHash";

    String[] TF_CONTENT_FIELDS = { TF_CONTENT + 0, TF_CONTENT + 1, TF_CONTENT + 2,
            TF_CONTENT + 3, TF_CONTENT + 4, TF_CONTENT + 5 };

    String[] CONTENT_FIELDS = { CONTENT + 0, CONTENT + 1, CONTENT + 2,
            CONTENT + 3, CONTENT + 4, CONTENT + 5 };

    String TRANS_UNIT_VARIANT_FIELD = "tuv.";

    String GLOSSARY_QUALIFIED_NAME = "glossaryEntry.glossary.qualifiedName";

    String PROJECT_VERSION_ID_FIELD = "projectVersionId";

    /** Full slug without tokenisation. */
    String FULL_SLUG_FIELD = "fullSlug";
}
