package org.zanata.rest.dto.resource;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import org.zanata.common.Namespaces;
import org.zanata.rest.dto.DTOUtil;
import org.zanata.rest.dto.HasSample;

/**
 *
 * This class is only used for generating the schema, as List<ResourceMeta>
 * serializes better across Json and XML.
 *
 * @author asgeirf
 *
 */
public class ResourceMetaList implements Serializable,
        HasSample<ResourceMetaList> {

    private static final long serialVersionUID = -3469563349425397350L;
    private List<ResourceMeta> resources;

    @JsonProperty("resource")
    @JsonValue
    public List<ResourceMeta> getResources() {
        if (resources == null) {
            resources = new ArrayList<ResourceMeta>();
        }
        return resources;
    }

    @Override
    public ResourceMetaList createSample() {
        ResourceMetaList entity = new ResourceMetaList();
        entity.getResources().addAll(new ResourceMeta().createSamples());
        return entity;
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
                prime * result
                        + ((resources == null) ? 0 : resources.hashCode());
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
        if (!(obj instanceof ResourceMetaList)) {
            return false;
        }
        ResourceMetaList other = (ResourceMetaList) obj;
        if (resources == null) {
            if (other.resources != null) {
                return false;
            }
        } else if (!resources.equals(other.resources)) {
            return false;
        }
        return true;
    }

}
