package org.zanata.rest.dto.resource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;

import java.util.ArrayList;
import java.util.List;

/**
 * A series of text flows to be translated and sharing common metadata.
 */
@JsonPropertyOrder({ "name", "contentType", "lang", "extensions", "textFlows" })
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Resource extends AbstractResourceMeta {

    private static final long serialVersionUID = 1L;
    private List<TextFlow> textFlows;

    public Resource() {
    }

    public Resource(String name) {
        super(name);
    }

    /**
     * Set of text flows containing the translatable strings.
     */
    @JsonProperty("text-flows")
    public List<TextFlow> getTextFlows() {
        if (textFlows == null) {
            textFlows = new ArrayList<TextFlow>();
        }
        return textFlows;
    }

    // @Override
    // public String toString()
    // {
    // return DTOUtil.toJSON(this);
    // }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCodeHelper();
        result =
                prime * result
                        + ((textFlows == null) ? 0 : textFlows.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof Resource)) {
            return false;
        }
        Resource other = (Resource) obj;
        if (!super.equalsHelper(other)) {
            return false;
        }
        if (textFlows == null) {
            if (other.textFlows != null) {
                return false;
            }
        } else if (!textFlows.equals(other.textFlows)) {
            return false;
        }
        return true;
    }

}
