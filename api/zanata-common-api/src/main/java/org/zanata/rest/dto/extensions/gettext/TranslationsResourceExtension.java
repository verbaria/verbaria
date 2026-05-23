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
@JsonSubTypes({ @Type(value = PoTargetHeader.class, name = "po-target-header") })
@JsonTypeName("TranslationResourceExtension")
@XmlSeeAlso({ PoTargetHeader.class })
@XmlTransient
public interface TranslationsResourceExtension extends ExtensionValue {

}
