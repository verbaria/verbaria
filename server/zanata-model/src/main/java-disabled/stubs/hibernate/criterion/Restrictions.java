package org.hibernate.criterion;
/** Stub for the removed Hibernate 5 Restrictions factory.
 * TODO: port all callers to jakarta.persistence.criteria.CriteriaBuilder. */
public final class Restrictions {
    private Restrictions() {}
    public static Criterion eq(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion ne(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion isNull(String propertyName) { return new Criterion() {}; }
    public static Criterion isNotNull(String propertyName) { return new Criterion() {}; }
    public static Criterion like(String propertyName, String value) { return new Criterion() {}; }
    public static Criterion like(String propertyName, String value, MatchMode mode) { return new Criterion() {}; }
    public static Criterion ilike(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion ilike(String propertyName, String value, MatchMode mode) { return new Criterion() {}; }
    public static Criterion and(Criterion... criteria) { return new Criterion() {}; }
    public static Criterion or(Criterion... criteria) { return new Criterion() {}; }
    public static Criterion not(Criterion criterion) { return new Criterion() {}; }
    public static Criterion in(String propertyName, Object[] values) { return new Criterion() {}; }
    public static Criterion in(String propertyName, java.util.Collection<?> values) { return new Criterion() {}; }
    public static Criterion ge(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion gt(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion le(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion lt(String propertyName, Object value) { return new Criterion() {}; }
    public static Criterion between(String propertyName, Object lo, Object hi) { return new Criterion() {}; }
    public static Criterion sqlRestriction(String sql) { return new Criterion() {}; }
}
