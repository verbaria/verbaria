package org.zanata.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public final class JsonUtil {

    private JsonUtil() {}

    public static String toJson(String json) {
        return toJson(json, false);
    }

    public static String toJson(String json, boolean pretty) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Object value = mapper.readValue(json, Object.class);
            return write(mapper, value, pretty);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toJson(Map<String, Object> jsonMap) {
        return toJson(jsonMap, false);
    }

    public static String toJson(Map<String, Object> jsonMap, boolean pretty) {
        try {
            return write(new ObjectMapper(), jsonMap, pretty);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Map<String, Object> toMap(String json) {
        try {
            return new ObjectMapper().readValue(json,
                    new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String write(ObjectMapper mapper, Object value, boolean pretty)
            throws IOException {
        return pretty
                ? mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value)
                : mapper.writeValueAsString(value);
    }
}
