package org.zanata.rest.dto.extensions.gettext;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.zanata.rest.dto.DTOUtil;

/**
 * Holds gettext message-level metadata for a source document.
 *
 * @author Sean Flanigan <a
 *         href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 *
 */
public class PotEntryHeader implements TextFlowExtension {

    public static final String ID = "gettext";
    private static final long serialVersionUID = 962567923295656414L;

    private String context;
    private List<String> flags;
    private List<String> references;
    @Deprecated
    // use TextFlow's SimpleComment extension
    private String extractedComment;

    @JsonProperty("context")
    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    @JsonProperty("extractedComment")
    @Deprecated
    // use TextFlow's SimpleComment extension
            public
            String getExtractedComment() {
        return extractedComment;
    }

    @Deprecated
    // use TextFlow's SimpleComment extension
            public
            void setExtractedComment(String comment) {
        this.extractedComment = comment;
    }

    @JsonProperty("flags")
    public List<String> getFlags() {
        if (flags == null)
            flags = new ArrayList<String>();
        return flags;
    }

    @JsonProperty("source-references")
    public List<String> getReferences() {
        if (references == null)
            references = new ArrayList<String>();
        return references;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((context == null) ? 0 : context.hashCode());
        result =
                prime
                        * result
                        + ((extractedComment == null) ? 0 : extractedComment
                                .hashCode());
        result = prime * result + ((flags == null) ? 0 : flags.hashCode());
        result =
                prime * result
                        + ((references == null) ? 0 : references.hashCode());
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
        if (!(obj instanceof PotEntryHeader)) {
            return false;
        }
        PotEntryHeader other = (PotEntryHeader) obj;
        if (context == null) {
            if (other.context != null) {
                return false;
            }
        } else if (!context.equals(other.context)) {
            return false;
        }
        if (extractedComment == null) {
            if (other.extractedComment != null) {
                return false;
            }
        } else if (!extractedComment.equals(other.extractedComment)) {
            return false;
        }
        if (flags == null) {
            if (other.flags != null) {
                return false;
            }
        } else if (!flags.equals(other.flags)) {
            return false;
        }
        if (references == null) {
            if (other.references != null) {
                return false;
            }
        } else if (!references.equals(other.references)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

}
