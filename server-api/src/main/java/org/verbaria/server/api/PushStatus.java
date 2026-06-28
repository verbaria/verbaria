package org.verbaria.server.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PushStatus(State status, int total, int processed,
        List<PushImportedEntry> imported, String error,
        Map<String, Object> lock) {

    public enum State {
        RUNNING, DONE, ERROR
    }

    public boolean done() {
        return status == State.DONE;
    }

    public boolean failed() {
        return status == State.ERROR;
    }
}
