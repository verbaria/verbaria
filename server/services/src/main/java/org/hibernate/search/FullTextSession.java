package org.hibernate.search;
import org.hibernate.Session;
/** Stub for removed Hibernate Search 5 FullTextSession. */
public interface FullTextSession extends Session {
    Object createFullTextQuery(Object luceneQuery, Class<?>... entities);
    Object getSearchFactory();
    void index(Object entity);
    void purge(Class<?> entityType, java.io.Serializable id);
    void purgeAll(Class<?> entityType);
    void flushToIndexes();
}
