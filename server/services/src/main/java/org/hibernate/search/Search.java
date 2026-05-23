package org.hibernate.search;
import org.hibernate.Session;
/** Stub for removed Hibernate Search 5 Search facade. */
public final class Search {
    private Search() {}
    public static FullTextSession getFullTextSession(Session session) {
        throw new UnsupportedOperationException("Hibernate Search 5 facade is disabled. Port to Hibernate Search 7 SearchSession.");
    }
}
