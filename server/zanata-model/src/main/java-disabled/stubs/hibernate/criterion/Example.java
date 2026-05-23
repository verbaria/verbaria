package org.hibernate.criterion;
/** Stub for removed Hibernate 5 Example. */
public final class Example implements Criterion {
    private Example() {}
    public static Example create(Object exampleEntity) { return new Example(); }
    public Example ignoreCase() { return this; }
    public Example enableLike() { return this; }
    public Example enableLike(MatchMode mode) { return this; }
    public Example excludeNone() { return this; }
    public Example excludeZeroes() { return this; }
    public Example excludeProperty(String name) { return this; }
}
