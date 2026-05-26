package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.zanata.rest.MediaTypes.Format;

public interface HasMediaType {

    @JsonIgnore
    String getMediaType(Format format);
}
