package org.hibernate.search.jpa;
import jakarta.persistence.EntityManager;
/** Stub for removed Hibernate Search 5 Search facade. */
public final class Search {
    private Search() {}
    public static FullTextEntityManager getFullTextEntityManager(EntityManager em) {
        throw new UnsupportedOperationException("Hibernate Search 5 facade is disabled. Port to Hibernate Search 7 SearchSession.");
    }
}
