package org.hibernate.search.bridge;
import java.util.Map;
public interface ParameterizedBridge {
    void setParameterValues(Map<String, String> parameters);
}
