/*
 * Copyright 2014, Red Hat, Inc. and individual contributors
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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.LocaleId;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({"localeId", "displayName", "alias", "nativeName", "enabled", "enabledByDefault", "pluralForms"})
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class LocaleDetails implements Serializable {

    private static final long serialVersionUID = -8133147543880728788L;
    private LocaleId localeId;
    private String displayName;
    private String alias;
    private String nativeName;
    private boolean enabled;
    private boolean enabledByDefault;
    private String pluralForms;
    private boolean rtl;

    // TODO check if no args constructor is needed
    public LocaleDetails() {
        this(null, null, null, null, false, false, null, false);
    }

    public LocaleDetails(LocaleId localeId, String displayName, String alias,
        String nativeName, boolean enabled, boolean enabledByDefault,
        String pluralForms, boolean rtl) {
        this.localeId = localeId;
        this.displayName = displayName;
        this.alias = alias;
        this.nativeName = nativeName;
        this.enabled = enabled;
        this.enabledByDefault = enabledByDefault;
        this.pluralForms = pluralForms;
        this.rtl = rtl;
    }

    /**
     * Unique locale identifier
     */
    @JsonProperty("localeId")
    @NotNull
    public LocaleId getLocaleId() {
      return localeId;
    }

    public void setLocaleId(LocaleId localeId) {
      this.localeId = localeId;
    }

    /**
     * Locale's display name (in English)
     */
    @JsonProperty("displayName")
    public String getDisplayName() {
      return displayName;
    }

    public void setDisplayName(String displayName) {
      this.displayName = displayName;
    }

    /**
     * An alternative name (if present) for this locale
     */
    @JsonProperty("alias")
    public String getAlias() {
      return alias;
    }

    public void setAlias(String alias) {
      this.alias = alias;
    }

    @JsonProperty("nativeName")
    public String getNativeName() {
        return nativeName;
    }

    public void setNativeName(String nativeName) {
        this.nativeName = nativeName;
    }

    /**
     * Indicates whether the locale is enabled in the system or not.
     */
    @JsonProperty("enabled")
    @NotNull
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Indicates whether the locale will be used automatically by the system.
     * e.g. when creating a new project, 'enabled by default' locales will
     * automatically be added to the project unless specifically indicating so.
     */
    @JsonProperty("enabledByDefault")
    @NotNull
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }

    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }

    /**
     * A string describing the formula for the locale's plural forms
     */
    @JsonProperty("pluralForms")
    public String getPluralForms() {
        return pluralForms;
    }

    public void setPluralForms(String pluralForms) {
        this.pluralForms = pluralForms;
    }

    /**
     * Indicates if this locale is Right-to-Left
     */
    @JsonProperty("rtl")
    public boolean isRtl() {
        return rtl;
    }

    public void setRTL(boolean rtl) {
        this.rtl = rtl;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LocaleDetails that = (LocaleDetails) o;

        if (enabled != that.enabled) return false;
        if (enabledByDefault != that.enabledByDefault) return false;
        if (rtl != that.rtl) return false;
        if (localeId != null ? !localeId.equals(that.localeId) :
                that.localeId != null) return false;
        if (displayName != null ? !displayName.equals(that.displayName) :
                that.displayName != null) return false;
        if (alias != null ? !alias.equals(that.alias) : that.alias != null)
            return false;
        if (nativeName != null ? !nativeName.equals(that.nativeName) :
                that.nativeName != null) return false;
        return pluralForms != null ? pluralForms.equals(that.pluralForms) :
                that.pluralForms == null;
    }

    @Override
    public int hashCode() {
        int result = localeId != null ? localeId.hashCode() : 0;
        result = 31 * result +
                (displayName != null ? displayName.hashCode() : 0);
        result = 31 * result + (alias != null ? alias.hashCode() : 0);
        result = 31 * result + (nativeName != null ? nativeName.hashCode() : 0);
        result = 31 * result + (enabled ? 1 : 0);
        result = 31 * result + (enabledByDefault ? 1 : 0);
        result = 31 * result +
                (pluralForms != null ? pluralForms.hashCode() : 0);
        result = 31 * result + (rtl ? 1 : 0);
        return result;
    }
}
