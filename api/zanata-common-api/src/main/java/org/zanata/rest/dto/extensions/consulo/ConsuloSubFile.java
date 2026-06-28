package org.zanata.rest.dto.extensions.consulo;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.zanata.rest.dto.DTOUtil;
import org.zanata.rest.dto.extensions.ContentAwareExtension;
import org.zanata.rest.dto.extensions.ParameterizedExtension;
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
 * Yaml entries are not raw files, so they carry no {@code extension}; instead
 * such an entry may declare the positional placeholders in its {@code text}
 * ({@code {0}}, {@code {1}}, ...) via the parallel {@code names} / {@code types}
 * lists. A single consulo extension row therefore holds either the raw-file
 * extension or the message parameters — they never coexist.
 *
 * @see org.zanata.rest.dto.extensions.gettext.TextFlowExtension
 */
@JsonTypeName(value = "consulo-sub-file")
public class ConsuloSubFile
        implements TextFlowExtension, ContentAwareExtension,
        ParameterizedExtension {

    public static final String ID = "consulo";
    private static final long serialVersionUID = 1L;

    /** The source file's extension, without the leading dot (may be empty). */
    private String extension;
    /** Names of the {@code {0}}, {@code {1}}, ... placeholders in {@code text}. */
    private List<String> names;
    /** Value types of those placeholders, index-aligned with {@link #names}. */
    private List<String> types;
    /** The mnemonic (accelerator) character, lifted out of {@code text}. */
    private String mnemonic;
    /** Index of the mnemonic character within the plain {@code text}. */
    private Integer mnemonicIndex;

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
    @JsonProperty("names")
    @JsonInclude(Include.NON_EMPTY)
    public List<String> getParamNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }

    @Override
    @JsonProperty("types")
    @JsonInclude(Include.NON_EMPTY)
    public List<String> getParamTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    @JsonProperty("mnemonic")
    @JsonInclude(Include.NON_NULL)
    public String getMnemonic() {
        return mnemonic;
    }

    public void setMnemonic(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    @JsonProperty("mnemonicIndex")
    @JsonInclude(Include.NON_NULL)
    public Integer getMnemonicIndex() {
        return mnemonicIndex;
    }

    public void setMnemonicIndex(Integer mnemonicIndex) {
        this.mnemonicIndex = mnemonicIndex;
    }

    @Override
    @JsonIgnore
    public String getContentType() {
        return extension;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(extension, names, types, mnemonic,
                mnemonicIndex);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ConsuloSubFile other)) {
            return false;
        }
        return java.util.Objects.equals(extension, other.extension)
                && java.util.Objects.equals(names, other.names)
                && java.util.Objects.equals(types, other.types)
                && java.util.Objects.equals(mnemonic, other.mnemonic)
                && java.util.Objects.equals(mnemonicIndex, other.mnemonicIndex);
    }
}
