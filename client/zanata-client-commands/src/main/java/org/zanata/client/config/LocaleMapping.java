package org.zanata.client.config;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import org.zanata.rest.dto.DTOUtil;

/**
 * @author Sean Flanigan <sflaniga@redhat.com>
 *
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LocaleMapping implements Serializable {
    private static final long serialVersionUID = 1L;

    private String locale;
    private String mapFrom;

    public LocaleMapping() {
        this(null, null);
    }

    /**
     * BCP47 locale ID
     *
     * @param localeID
     */
    public LocaleMapping(String localeID) {
        this(localeID, null);
    }

    /**
     *
     *
     * @param localeID
     *            BCP47 locale ID
     * @param mapFrom
     *            locale ID used in local/client project
     */
    public LocaleMapping(String localeID, String mapFrom) {
        this.locale = localeID;
        this.mapFrom = mapFrom;
    }

    /**
     * BCP47 locale ID
     */
    public String getLocale() {
        return locale;
    }

    public void setLocale(String localeID) {
        this.locale = localeID;
    }

    public String getMapFrom() {
        return mapFrom;
    }

    public void setMapFrom(String localID) {
        this.mapFrom = localID;
    }

    @JsonIgnore
    public String getLocalLocale() {
        if (mapFrom != null)
            return mapFrom;
        else
            return locale;
    }

    @JsonIgnore
    public String getJavaLocale() {
        return getLocalLocale().replace('-', '_');
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((locale == null) ? 0 : locale.hashCode());
        result = prime * result + ((mapFrom == null) ? 0 : mapFrom.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof LocaleMapping)) {
            return false;
        }
        LocaleMapping other = (LocaleMapping) obj;
        if (locale == null) {
            if (other.locale != null) {
                return false;
            }
        } else if (!locale.equals(other.locale)) {
            return false;
        }
        if (mapFrom == null) {
            if (other.mapFrom != null) {
                return false;
            }
        } else if (!mapFrom.equals(other.mapFrom)) {
            return false;
        }
        return true;
    }

}
