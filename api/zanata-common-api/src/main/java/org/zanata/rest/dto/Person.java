package org.zanata.rest.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import org.zanata.common.Namespaces;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonPropertyOrder({ "email", "name", "links" })
@JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
public class Person implements Serializable, HasSample<Person> {

    private static final long serialVersionUID = 7686060899220005351L;
    private String email;
    private String name;

    private Links links;

    public Person() {
    }

    public Person(String email, String name) {
        this.email = email;
        this.name = name;
    }

    @JsonProperty("email")
    @Email
    @NotNull
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @JsonProperty("name")
    @NotEmpty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Set of links managed by this resource
     *
     * This field is ignored in PUT/POST operations
     *
     * @return set of Links managed by this resource
     */
    @JsonProperty("link")
    public Links getLinks() {
        return links;
    }

    public void setLinks(Links links) {
        this.links = links;
    }

    public Links getLinks(boolean createIfNull) {
        if (createIfNull && links == null)
            links = new Links();
        return links;
    }

    @Override
    public Person createSample() {
        return new Person("me@example.com", "Mr. Example");
    }

    @Override
    public String toString() {
        return DTOUtil.toJSON(this);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((email == null) ? 0 : email.hashCode());
        result = prime * result + ((links == null) ? 0 : links.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        if (!(obj instanceof Person)) {
            return false;
        }
        Person other = (Person) obj;
        if (email == null) {
            if (other.email != null) {
                return false;
            }
        } else if (!email.equals(other.email)) {
            return false;
        }
        if (links == null) {
            if (other.links != null) {
                return false;
            }
        } else if (!links.equals(other.links)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

}
