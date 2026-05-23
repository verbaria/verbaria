package org.hibernate.type;
/** Stub for the removed Hibernate 5 LiteralType. */
public interface LiteralType<T> {
    String objectToSQLString(T value, org.hibernate.dialect.Dialect dialect) throws Exception;
}
