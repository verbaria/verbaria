/*
 * Copyright 2011, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.rest.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.LocaleId;
import org.zanata.common.Namespaces;
import org.zanata.rest.MediaTypes;

/**
 * A single glossary entry representing a single translated term in multiple
 * locales.
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 *
 **/
@JsonPropertyOrder({ "id", "pos", "description", "srcLang", "sourceReference", "glossaryTerms", "termsCount", "qualifiedName" })
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class GlossaryEntry implements Serializable, HasMediaType {
    /**
    *
    */
    private static final long serialVersionUID = 1685907304736580890L;

    private Long id;

    @Size(max = 255)
    private String pos;

    @Size(max = 500)
    private String description;

    private List<GlossaryTerm> glossaryTerms;

    private LocaleId srcLang;

    @Size(max = 500)
    private String sourceReference;

    private QualifiedName qualifiedName;

    private int termsCount = 0;

    public GlossaryEntry() {
        this(null);
    }

    public GlossaryEntry(Long id) {
        this.id = id;
    }

    /**
     * Unique identifier
     */
    @JsonProperty("id")
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    /**
     * Glossary entry's part of speech
     */
    @JsonProperty("pos")
    public String getPos() {
        return pos;
    }

    public void setPos(String pos) {
        this.pos = pos;
    }

    @JsonProperty("description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Number of translated terms. A term is the glossary entry's representation
     * for a specific locale
     */
    @JsonProperty("termsCount")
    public int getTermsCount() {
        return termsCount;
    }

    public void setTermsCount(int termsCount) {
        this.termsCount = termsCount;
    }

    /**
     * The full list of glossary terms
     */
    @JsonProperty("glossary-term")
    public List<GlossaryTerm> getGlossaryTerms() {
        if (glossaryTerms == null) {
            glossaryTerms = new ArrayList<GlossaryTerm>();
        }
        return glossaryTerms;
    }

    public void setGlossaryTerms(List<GlossaryTerm> glossaryTerms) {
        this.glossaryTerms = glossaryTerms;
    }

    /**
     * The source locale for this specific entry
     */
    @JsonProperty("src-lang")
    public LocaleId getSrcLang() {
        return srcLang;
    }

    public void setSrcLang(LocaleId srcLang) {
        this.srcLang = srcLang;
    }

    @JsonProperty("source-reference")
    public String getSourceReference() {
        return sourceReference;
    }

    public void setSourceReference(String ref) {
        this.sourceReference = ref;
    }

    @JsonProperty("qualified-name")
    public QualifiedName getQualifiedName() {
        return qualifiedName;
    }

    public void setQualifiedName(QualifiedName qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GlossaryEntry)) return false;

        GlossaryEntry that = (GlossaryEntry) o;

        if (termsCount != that.termsCount) return false;
        if (description != null ? !description.equals(that.description) :
            that.description != null) return false;
        if (glossaryTerms != null ? !glossaryTerms.equals(that.glossaryTerms) :
            that.glossaryTerms != null) return false;
        if (pos != null ? !pos.equals(that.pos) : that.pos != null)
            return false;
        if (sourceReference != null ?
            !sourceReference.equals(that.sourceReference) :
            that.sourceReference != null) return false;
        if (srcLang != null ? !srcLang.equals(that.srcLang) :
            that.srcLang != null)
            return false;
        return qualifiedName != null ?
            qualifiedName.equals(that.qualifiedName) :
            that.qualifiedName == null;

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (pos != null ? pos.hashCode() : 0);
        result =
            31 * result + (description != null ? description.hashCode() : 0);
        result =
            31 * result +
                (glossaryTerms != null ? glossaryTerms.hashCode() : 0);
        result = 31 * result + (srcLang != null ? srcLang.hashCode() : 0);
        result = 31 * result +
            (sourceReference != null ? sourceReference.hashCode() : 0);
        result =
            31 * result +
                (qualifiedName != null ? qualifiedName.hashCode() : 0);
        result = 31 * result + termsCount;
        return result;
    }

    @Override
    public String getMediaType(MediaTypes.Format format) {
        return MediaTypes.APPLICATION_ZANATA_GLOSSARY + format;
    }
}
