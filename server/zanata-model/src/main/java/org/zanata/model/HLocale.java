/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.model;

import com.ibm.icu.util.ULocale;
import io.leangen.graphql.annotations.GraphQLQuery;
import io.leangen.graphql.annotations.types.GraphQLType;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.zanata.common.LocaleId;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Entity
@Cacheable
@GraphQLType(name = "Locale")
public class HLocale extends ModelEntityBase implements Serializable, HasUserFriendlyToString {

    private static final long serialVersionUID = 1L;

    private LocaleId localeId;
    private boolean active;
    private boolean enabledByDefault;
    private Set<HProject> supportedProjects;
    private Set<HProjectIteration> supportedIterations;
    private Set<HLocaleMember> members;
    private String pluralForms;
    private String displayName;
    private String nativeName;
    private String aiPrompt;

    public HLocale() {
    }

    public HLocale(LocaleId localeId) {
        this.localeId = localeId;
    }

    public HLocale(LocaleId localeId, boolean enabledByDefault, boolean active) {
        this.localeId = localeId;
        this.enabledByDefault = enabledByDefault;
        this.active = active;
    }

    @NaturalId
    @NotNull
        @GraphQLQuery(name = "localeId", description = "localeId")
    public LocaleId getLocaleId() {
        return localeId;
    }

    public void setLocaleId(LocaleId localeId) {
        this.localeId = localeId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    @ManyToMany
    @JoinTable(
            name = "HProject_Locale",
            joinColumns = @JoinColumn(name = "localeId"),
            inverseJoinColumns = @JoinColumn(name = "projectId"))
    public Set<HProject> getSupportedProjects() {
        if (supportedProjects == null) supportedProjects = new HashSet<>();
        return supportedProjects;
    }

    public void setSupportedProjects(Set<HProject> supportedProjects) {
        this.supportedProjects = supportedProjects;
    }

    @ManyToMany
    @JoinTable(
            name = "HProjectIteration_Locale",
            joinColumns = @JoinColumn(name = "localeId"),
            inverseJoinColumns = @JoinColumn(name = "projectIterationId"))
    public Set<HProjectIteration> getSupportedIterations() {
        if (supportedIterations == null) supportedIterations = new HashSet<>();
        return supportedIterations;
    }

    public void setSupportedIterations(Set<HProjectIteration> supportedIterations) {
        this.supportedIterations = supportedIterations;
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "id.supportedLanguage")
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    public Set<HLocaleMember> getMembers() {
        if (members == null) members = new HashSet<>();
        return members;
    }

    public void setMembers(Set<HLocaleMember> members) {
        this.members = members;
    }

    public String getPluralForms() {
        return pluralForms;
    }

    public void setPluralForms(String pluralForms) {
        this.pluralForms = pluralForms;
    }

    @GraphQLQuery(name = "name", description = "name of the language")
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    @Column(columnDefinition = "text")
    public String getAiPrompt() {
        return aiPrompt;
    }

    public void setAiPrompt(String aiPrompt) {
        this.aiPrompt = aiPrompt;
    }

    public String retrieveNativeName() {
        if (nativeName == null || nativeName.isEmpty()) return retrieveDefaultNativeName();
        return nativeName;
    }

    public String retrieveDisplayName() {
        if (displayName == null || displayName.isEmpty()) return retrieveDefaultDisplayName();
        return displayName;
    }

    public String retrieveDefaultNativeName() {
        ULocale ul = asULocale();
        return ul.getDisplayName(ul);
    }

    public String retrieveDefaultDisplayName() {
        return asULocale().getDisplayName();
    }

    public ULocale asULocale() {
        return new ULocale(this.localeId.getId());
    }

    @Override
    public String userFriendlyToString() {
        return String.format("Locale(id=%s, name=%s)", localeId, retrieveDisplayName());
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (!(other instanceof HLocale)) return false;
        HLocale o = (HLocale) other;
        return this.localeId != null && this.localeId.equals(o.localeId);
    }

    @Override
    public int hashCode() {
        return 59 + (localeId == null ? 0 : localeId.hashCode());
    }

    @Override
    public String toString() {
        return "HLocale(localeId=" + localeId + ")";
    }
}
