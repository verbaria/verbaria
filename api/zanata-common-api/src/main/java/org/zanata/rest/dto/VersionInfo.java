package org.zanata.rest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.zanata.common.Namespaces;

/**
 * Holds system version information
 */
@JsonTypeName(value = "versionType")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public final class VersionInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private String versionNo;
    private String buildTimeStamp;
    private String scmDescribe;

    public VersionInfo(String versionNo, String buildTimestamp, String scmDescribe) {
        this.versionNo = versionNo;
        this.buildTimeStamp = buildTimestamp;
        this.scmDescribe = scmDescribe;
    }

    @Deprecated
    public VersionInfo(String versionNo, String buildTimestamp) {
        this(versionNo, buildTimestamp, "UNKNOWN");
    }

    public VersionInfo() {
    }

    public VersionInfo(VersionInfo other) {
        this(other.versionNo, other.buildTimeStamp, other.scmDescribe);
    }

    /**
     * Version number
     */
    @JsonProperty("versionNo")
    public String getVersionNo() {
        return versionNo;
    }

    /**
     * ISO8601 timestamp for when the current system version was built
     */
    @JsonProperty("buildTimeStamp")
    public String getBuildTimeStamp() {
        return buildTimeStamp;
    }

    /**
     * Identifier for the current version in source control
     */
    @JsonProperty("scmDescribe")
    public String getScmDescribe() {
        return scmDescribe;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public void setBuildTimeStamp(String buildTimestamp) {
        this.buildTimeStamp = buildTimestamp;
    }

    public void setScmDescribe(String scmDescribe) {
        this.scmDescribe = scmDescribe;
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result =
                prime
                        * result
                        + ((buildTimeStamp == null) ? 0 : buildTimeStamp
                                .hashCode());
        result =
                prime
                        * result
                        + ((scmDescribe == null) ? 0 : scmDescribe
                                .hashCode());
        result =
                prime * result
                        + ((versionNo == null) ? 0 : versionNo.hashCode());
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
        if (!(obj instanceof VersionInfo)) {
            return false;
        }
        VersionInfo other = (VersionInfo) obj;
        if (buildTimeStamp == null) {
            if (other.buildTimeStamp != null) {
                return false;
            }
        } else if (!buildTimeStamp.equals(other.buildTimeStamp)) {
            return false;
        }
        if (scmDescribe == null) {
            if (other.scmDescribe != null) {
                return false;
            }
        } else if (!scmDescribe.equals(other.scmDescribe)) {
            return false;
        }
        if (versionNo == null) {
            if (other.versionNo != null) {
                return false;
            }
        } else if (!versionNo.equals(other.versionNo)) {
            return false;
        }
        return true;
    }

}
