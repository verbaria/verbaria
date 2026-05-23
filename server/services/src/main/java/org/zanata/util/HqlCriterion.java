package org.zanata.util;

/**
 * To make writing HQL easier.
 * @author Patrick Huang
 *         <a href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public class HqlCriterion {

    /**
     * Wildcard placement for SQL {@code LIKE}. Replaces the removed Hibernate 5
     * {@code org.hibernate.criterion.MatchMode}.
     */
    public enum MatchMode {
        EXACT     { @Override public String toMatchString(String pattern) { return pattern; } },
        START     { @Override public String toMatchString(String pattern) { return pattern + "%"; } },
        END       { @Override public String toMatchString(String pattern) { return "%" + pattern; } },
        ANYWHERE  { @Override public String toMatchString(String pattern) { return "%" + pattern + "%"; } };
        public abstract String toMatchString(String pattern);
    }

    public static String eq(String property, String namedParam) {
        return property + "=" + namedParam;
    }

    public static String ne(String property, String namedParam) {
        return property + "<>" + namedParam;
    }

    public static String isNull(String property) {
        return property + " is null";
    }

    public static String ilike(String property, String namedParam) {
        return "lower(" + property + ") like " + namedParam +"";
    }

    public static String like(String property, String namedParam) {
        return property + " like " + namedParam;
    }

    public static String like(String property, boolean caseSensitive, String namedParam) {
        if (caseSensitive) {
            return like(property, namedParam);
        } else {
            return ilike(property, namedParam);
        }
    }

    public static String escapeWildcard(String value) {
        return value.replaceAll("%", "\\\\%").replaceAll("_", "\\\\_");
    }

    public static String match(String pattern, MatchMode matchMode) {
        return matchMode.toMatchString(pattern);
    }

    public static String gt(String property, String namedParam) {
        return property + ">" + namedParam;
    }

    public static String lt(String property, String namedParam) {
        return property + "<" + namedParam;
    }
}
