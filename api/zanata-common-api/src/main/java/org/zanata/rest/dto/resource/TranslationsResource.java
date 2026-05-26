package org.zanata.rest.dto.resource;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.webcohesion.enunciate.metadata.Label;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;
import org.zanata.rest.dto.Extensible;
import org.zanata.rest.dto.HasSample;
import org.zanata.rest.dto.Links;
import org.zanata.rest.dto.extensions.gettext.TranslationsResourceExtension;

/**
 * Represents the translation of a document into a single locale.
 */
@JsonPropertyOrder({ "links", "extensions", "textFlowTargets" })
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
@Label("Translations")
public class TranslationsResource implements Serializable,
        HasSample<TranslationsResource>,
        Extensible<TranslationsResourceExtension> {
    private static final long serialVersionUID = 1L;
    private ExtensionSet<TranslationsResourceExtension> extensions;
    private Links links;
    private List<TextFlowTarget> textFlowTargets;
    private Integer revision;

    /**
     * Any provided extensions
     */
    @JsonProperty("extensions")
    public ExtensionSet<TranslationsResourceExtension> getExtensions() {
        return extensions;
    }

    public void setExtensions(
            ExtensionSet<TranslationsResourceExtension> extensions) {
        this.extensions = extensions;
    }

    @JsonIgnore
    public ExtensionSet<TranslationsResourceExtension> getExtensions(
            boolean createIfNull) {
        if (createIfNull && extensions == null)
            extensions = new ExtensionSet<TranslationsResourceExtension>();
        return extensions;
    }

    /**
     * The text flow targets (i.e. translated text)
     */
    @JsonProperty("targets")
    public List<TextFlowTarget> getTextFlowTargets() {
        if (textFlowTargets == null) {
            textFlowTargets = new ArrayList<TextFlowTarget>();
        }
        return textFlowTargets;
    }

    /**
     * A collection of links provided with the translations.
     */
    @JsonProperty("links")
    public Links getLinks() {
        return links;
    }

    public void setLinks(Links links) {
        this.links = links;
    }

    @JsonIgnore
    public Links getLinks(boolean createIfNull) {
        if (createIfNull && links == null)
            links = new Links();
        return links;
    }

    /**
     * Revision number for the translations
     */
    public Integer getRevision() {
        return revision;
    }

    public void setRevision(Integer revision) {
        this.revision = revision;
    }

    @Override
    public TranslationsResource createSample() {
        return new TranslationsResource();
    }

    // @Override
    // public String toString()
    // {
    // return DTOUtil.toJSON(this);
    // }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime * result
                        + ((extensions == null) ? 0 : extensions.hashCode());
        result = prime * result + ((links == null) ? 0 : links.hashCode());
        result =
                prime
                        * result
                        + ((textFlowTargets == null) ? 0 : textFlowTargets
                                .hashCode());
        result =
                prime * result + ((revision == null) ? 0 : revision.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof TranslationsResource)) {
            return false;
        }
        TranslationsResource other = (TranslationsResource) obj;
        if (extensions == null) {
            if (other.extensions != null) {
                return false;
            }
        } else if (!extensions.equals(other.extensions)) {
            return false;
        }
        if (links == null) {
            if (other.links != null) {
                return false;
            }
        } else if (!links.equals(other.links)) {
            return false;
        }
        if (textFlowTargets == null) {
            if (other.textFlowTargets != null) {
                return false;
            }
        } else if (!textFlowTargets.equals(other.textFlowTargets)) {
            return false;
        }
        if (revision == null) {
            if (other.revision != null) {
                return false;
            }
        } else if (!revision.equals(other.revision)) {
            return false;
        }
        return true;
    }

}
