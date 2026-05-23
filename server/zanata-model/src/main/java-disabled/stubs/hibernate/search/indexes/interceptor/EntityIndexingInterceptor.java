package org.hibernate.search.indexes.interceptor;
public interface EntityIndexingInterceptor<T> {
    IndexingOverride onAdd(T entity);
    IndexingOverride onUpdate(T entity);
    IndexingOverride onDelete(T entity);
    IndexingOverride onCollectionUpdate(T entity);
}
