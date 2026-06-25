package org.zanata.rest.dto.extensions.chrome;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.zanata.rest.dto.DTOUtil;
import org.zanata.rest.dto.extensions.gettext.TextFlowExtension;

@JsonTypeName(value = "chrome-message")
public class ChromeMessage implements TextFlowExtension {

    public static final String ID = "chrome";
    private static final long serialVersionUID = 1L;

    private Map<String, Placeholder> placeholders = new LinkedHashMap<>();

    public ChromeMessage() {
    }

    @JsonProperty("placeholders")
    @JsonInclude(Include.NON_EMPTY)
    public Map<String, Placeholder> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(Map<String, Placeholder> placeholders) {
        this.placeholders = placeholders == null ? new LinkedHashMap<>()
                : new LinkedHashMap<>(placeholders);
    }

    public void putPlaceholder(String name, String content, String example) {
        placeholders.put(name, new Placeholder(content, example));
    }

    @JsonIgnore
    public boolean isEmpty() {
        return placeholders.isEmpty();
    }

    public static final class Placeholder implements Serializable {

        private static final long serialVersionUID = 1L;

        private String content;
        private String example;

        public Placeholder() {
        }

        public Placeholder(String content, String example) {
            this.content = content;
            this.example = example;
        }

        @JsonProperty("content")
        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @JsonProperty("example")
        @JsonInclude(Include.NON_NULL)
        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }

        @Override
        public int hashCode() {
            return Objects.hash(content, example);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Placeholder other)) {
                return false;
            }
            return Objects.equals(content, other.content)
                    && Objects.equals(example, other.example);
        }
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(placeholders);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ChromeMessage other)) {
            return false;
        }
        return Objects.equals(placeholders, other.placeholders);
    }
}
