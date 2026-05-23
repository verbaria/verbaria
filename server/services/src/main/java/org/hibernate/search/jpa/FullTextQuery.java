package org.hibernate.search.jpa;
import jakarta.persistence.Query;
public interface FullTextQuery extends Query {
    int getResultSize();
    FullTextFilter enableFullTextFilter(String name);
    FullTextQuery setProjection(String... fields);
    FullTextQuery setSort(Object sort);
}
