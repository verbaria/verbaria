package org.hibernate.search.bridge;
import org.apache.lucene.document.Document;
public interface LuceneOptions {
    void addFieldToDocument(String name, String indexedString, Document document);
    void addNumericFieldToDocument(String name, Object value, Document document);
}
