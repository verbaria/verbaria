package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

import com.webcohesion.enunciate.metadata.DocumentationExample;
import com.webcohesion.enunciate.metadata.Label;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;

/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "locale", "numberOfTerms"})
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Label("Glossary Locale Info")
public class GlossaryLocaleInfo implements Serializable {
    private static final long serialVersionUID = 7486128063191358182L;
    private LocaleDetails locale;
    private int numberOfTerms;

    public GlossaryLocaleInfo() {
        this(null, 0);
    }

    public GlossaryLocaleInfo(LocaleDetails locale, int numberOfTerms) {
        this.locale = locale;
        this.numberOfTerms = numberOfTerms;
    }

    @JsonProperty("locale")
    public LocaleDetails getLocale() {
        return locale;
    }

    public void setLocale(LocaleDetails locale) {
        this.locale = locale;
    }

    /**
     * Number of terms available for the glossary in this locale
     */
    @JsonProperty("numberOfTerms")
    @DocumentationExample("2")
    public int getNumberOfTerms() {
        return numberOfTerms;
    }

    public void setNumberOfTerms(int numberOfTerms) {
        this.numberOfTerms = numberOfTerms;
    }
}
