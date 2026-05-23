package org.hibernate.criterion;
/** Stub for removed Hibernate 5 MatchMode enum. */
public enum MatchMode {
    EXACT, START, END, ANYWHERE;
    public String toMatchString(String pattern) { return pattern; }
}
