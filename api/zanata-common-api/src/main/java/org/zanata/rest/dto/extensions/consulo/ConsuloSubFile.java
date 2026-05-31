package org.zanata.rest.dto.extensions.consulo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.zanata.rest.dto.DTOUtil;
import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

/**
 * Consulo-specific source metadata for a text flow.
 * <p>
 * For the consulo / YAML project type a document can be a directory of raw
 * sub-files (html, colorPage, properties, ...). Each such file becomes one text
 * flow whose key is the extension-stripped sub-path ({@code /} → {@code .}). The
 * sub-path is therefore recoverable from the key; the only thing that cannot be
 * derived is the file's <em>extension</em>, which this extension carries so that
 * pulling source can recreate the exact file without guessing or hardcoding
 * extensions. Its presence also flags the entry as a raw file rather than a
 * {@code key: text} yaml value, and surfaces the file type to translators.
 *
 * @see org.zanata.rest.dto.extensions.gettext.TextFlowExtension
 */
@JsonTypeName(value = "consulo-sub-file")
public class ConsuloSubFile implements TextFlowExtension {

    public static final String ID = "consulo";
    private static final long serialVersionUID = 1L;

    /** The source file's extension, without the leading dot (may be empty). */
    private String extension;

    public ConsuloSubFile() {
    }

    public ConsuloSubFile(String extension) {
        this.extension = extension;
    }

    @JsonProperty("extension")
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        return extension == null ? 0 : extension.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConsuloSubFile)) {
            return false;
        }
        ConsuloSubFile other = (ConsuloSubFile) obj;
        return extension == null ? other.extension == null
                : extension.equals(other.extension);
    }
}
