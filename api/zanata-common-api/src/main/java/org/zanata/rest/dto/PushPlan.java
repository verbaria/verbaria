package org.zanata.rest.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PushPlan(List<PushPlanEntry> entries,
        List<PushPlanUnmatched> unmatched) {

    public List<PushPlanEntry> entries() {
        return entries == null ? List.of() : entries;
    }

    public List<PushPlanUnmatched> unmatched() {
        return unmatched == null ? List.of() : unmatched;
    }
}
