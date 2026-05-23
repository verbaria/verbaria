package org.zanata.rest.dto;

import jakarta.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.zanata.rest.MediaTypes.Format;

public interface HasMediaType {

    @JsonIgnore
    @XmlTransient
    String getMediaType(Format format);
}
