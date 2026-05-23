/*
 * Holdover for org.hibernate.criterion.NaturalIdentifier: kept as a tiny
 * data carrier (attribute → value pairs) used by *Home classes to declare
 * how to look up an entity by natural id. SlugHome.loadInstance() reads
 * these and replays them through the Hibernate 6 byNaturalId() API.
 */
package org.hibernate.criterion;

import java.util.LinkedHashMap;
import java.util.Map;

public final class NaturalIdentifier {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public NaturalIdentifier set(String key, Object value) {
        values.put(key, value);
        return this;
    }

    public Map<String, Object> getValues() {
        return values;
    }
}
