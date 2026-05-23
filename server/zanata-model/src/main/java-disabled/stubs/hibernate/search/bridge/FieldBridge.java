package org.hibernate.search.bridge;
import org.apache.lucene.document.Document;
public interface FieldBridge {
    void set(String name, Object value, Document document, LuceneOptions luceneOptions);
}
