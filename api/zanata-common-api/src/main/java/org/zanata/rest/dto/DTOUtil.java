package org.zanata.rest.dto;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DTOUtil {
    private final static Logger log = LoggerFactory.getLogger(DTOUtil.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String toJSON(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (IOException e) {
            log.error("toJSON failed", e);
            return obj.getClass().getName() + "@"
                    + Integer.toHexString(obj.hashCode());
        }
    }

    public static <T> T fromJSONToObject(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            log.error("fromJSONToObject failed", e);
            return null;
        }
    }
}
