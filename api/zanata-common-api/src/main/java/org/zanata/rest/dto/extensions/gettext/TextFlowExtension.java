package org.zanata.rest.dto.extensions.gettext;

import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.zanata.rest.dto.ExtensionValue;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
        property = "object-type")
@JsonSubTypes({ @Type(value = PotEntryHeader.class, name = "pot-entry-header"),
        @Type(value = SimpleComment.class, name = "comment"),
        @Type(value = ConsuloSubFile.class, name = "consulo-sub-file") })
@JsonTypeName("TextFlowExtension")
public interface TextFlowExtension extends ExtensionValue {

}
