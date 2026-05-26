package org.zanata.rest.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;

/**
 * Wrapper for list of Glossary entries and a list of warning messages after
 * saving/updating
 *
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@JsonPropertyOrder({ "glossaryEntries", "warnings" })
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class GlossaryResults implements Serializable {
    private static final long serialVersionUID = 7100495681284134288L;
    private List<GlossaryEntry> glossaryEntries;
    private List<String> warnings;

    public GlossaryResults() {
    }

    public GlossaryResults(List<GlossaryEntry> glossaryEntries, List<String> warnings) {
        this.glossaryEntries = glossaryEntries;
        this.warnings = warnings;
    }

    /**
     * The list of created / updated glossary entries
     */
    @JsonProperty("glossaryEntries")
    public List<GlossaryEntry> getGlossaryEntries() {
        if (glossaryEntries == null) {
            glossaryEntries = new ArrayList<GlossaryEntry>();
        }
        return glossaryEntries;
    }

    /**
     * A list of warnings generated when performing the operation
     */
    @JsonProperty("warnings")
    public List<String> getWarnings() {
        if (warnings == null) {
            warnings = new ArrayList<String>();
        }
        return warnings;
    }

    public void setGlossaryEntries(List<GlossaryEntry> glossaryEntries) {
        this.glossaryEntries = glossaryEntries;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
