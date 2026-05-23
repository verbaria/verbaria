package org.hibernate.type.descriptor.java;
import org.hibernate.type.descriptor.WrapperOptions;
/** Stub for the removed Hibernate 5 AbstractTypeDescriptor. */
public abstract class AbstractTypeDescriptor<T> {
    protected AbstractTypeDescriptor(Class<T> type) {}
    public abstract T fromString(CharSequence sequence);
    public abstract <X> X unwrap(T value, Class<X> type, WrapperOptions options);
    public abstract <X> T wrap(X value, WrapperOptions options);
}
