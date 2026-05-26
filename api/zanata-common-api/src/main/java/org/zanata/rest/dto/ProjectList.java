package org.zanata.rest.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;
import org.zanata.common.Namespaces;

/**
 *
 * This class is only used for generating the schema.
 *
 * @author asgeirf
 *
 */
public class ProjectList implements Serializable, HasSample<ProjectList> {

    private static final long serialVersionUID = 2093156157626506919L;
    private List<Project> projects;

    @JsonValue
    public List<Project> getProjects() {
        if (projects == null) {
            projects = new ArrayList<Project>();
        }
        return projects;
    }

    @Override
    public ProjectList createSample() {
        ProjectList entity = new ProjectList();
        entity.getProjects().addAll(new Project().createSamples());
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
                prime * result + ((projects == null) ? 0 : projects.hashCode());
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
        if (!(obj instanceof ProjectList)) {
            return false;
        }
        ProjectList other = (ProjectList) obj;
        if (projects == null) {
            if (other.projects != null) {
                return false;
            }
        } else if (!projects.equals(other.projects)) {
            return false;
        }
        return true;
    }

}
