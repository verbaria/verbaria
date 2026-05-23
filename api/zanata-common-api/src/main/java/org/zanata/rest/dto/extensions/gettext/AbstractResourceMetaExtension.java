package org.zanata.rest.dto.extensions.gettext;

import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlTransient;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.zanata.rest.dto.ExtensionValue;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "object-type")
@JsonSubTypes({ @Type(value = PoHeader.class, name = "po-header") })
@JsonTypeName("AbstractResourceMetaExtension")
@XmlSeeAlso({ PoHeader.class })
@XmlTransient
public interface AbstractResourceMetaExtension extends ExtensionValue {

}
