package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;

/**
 * Information about a specific Glossary.
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "srcLocale", "transLocale"})
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class GlossaryInfo implements Serializable {
    private static final long serialVersionUID = -5688873815049369490L;
    private GlossaryLocaleInfo srcLocale;
    private List<GlossaryLocaleInfo> transLocale;

    public GlossaryInfo() {
        this(null, new ArrayList<GlossaryLocaleInfo>());
    }

    public GlossaryInfo(GlossaryLocaleInfo srcLocale,
        List<GlossaryLocaleInfo> transLocale) {
        this.srcLocale = srcLocale;
        this.transLocale = transLocale;
    }

    /**
     * The glossary's source locale
     */
    @JsonProperty("srcLocale")
    public GlossaryLocaleInfo getSrcLocale() {
        return srcLocale;
    }

    public void setSrcLocale(GlossaryLocaleInfo srcLocale) {
        this.srcLocale = srcLocale;
    }

    /**
     * The list of translated locale's available for the glossary
     */
    @JsonProperty("transLocale")
    public List<GlossaryLocaleInfo> getTransLocale() {
        return transLocale;
    }

    public void setTransLocale(List<GlossaryLocaleInfo> transLocale) {
        this.transLocale = transLocale;
    }
}

