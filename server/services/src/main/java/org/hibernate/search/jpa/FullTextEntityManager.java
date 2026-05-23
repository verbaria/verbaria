package org.hibernate.search.jpa;
import jakarta.persistence.EntityManager;
/** Stub for removed Hibernate Search 5 FullTextEntityManager. */
public interface FullTextEntityManager extends EntityManager {
    FullTextQuery createFullTextQuery(Object luceneQuery, Class<?>... entities);
    void index(Object entity);
    void purge(Class<?> entityType, java.io.Serializable id);
    void purgeAll(Class<?> entityType);
    void flushToIndexes();
    SearchFactory getSearchFactory();
}
