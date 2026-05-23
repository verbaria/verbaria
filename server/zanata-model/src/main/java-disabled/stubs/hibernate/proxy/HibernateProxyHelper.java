package org.hibernate.proxy;
/** Stub for removed Hibernate 5 HibernateProxyHelper. */
public final class HibernateProxyHelper {
    public static Class<?> getClassWithoutInitializingProxy(Object object) {
        return object == null ? null : object.getClass();
    }
}
