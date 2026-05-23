package org.hibernate.type;
/** Stub for the removed Hibernate 5 DiscriminatorType. */
public interface DiscriminatorType<T> {
    T stringToObject(String xml) throws Exception;
    String toString(T value) throws Exception;
}
