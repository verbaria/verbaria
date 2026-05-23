package org.hibernate.search.jpa;
/** Stub. The real SearchFactory was the Hibernate Search 5 entry point to
 * analyzer lookup, index info, etc. Returning nulls keeps callers compiling
 * but TM/full-text search is disabled at runtime. */
public interface SearchFactory {
    org.apache.lucene.analysis.Analyzer getAnalyzer(String name);
    org.apache.lucene.analysis.Analyzer getAnalyzer(Class<?> entityType);
}
