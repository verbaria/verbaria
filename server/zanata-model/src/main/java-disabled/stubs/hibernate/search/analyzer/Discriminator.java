package org.hibernate.search.analyzer;
public interface Discriminator {
    String getAnalyzerDefinitionName(Object value, Object entity, String field);
}
