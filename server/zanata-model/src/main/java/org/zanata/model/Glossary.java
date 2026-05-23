package org.zanata.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;


/**
 * @author Alex Eng <a href="mailto:aeng@redhat.com">aeng@redhat.com</a>
 */
@Entity
@Access(AccessType.FIELD)
@Table(uniqueConstraints = @UniqueConstraint(name = "Idx_qualifiedName",
        columnNames = "qualifiedName"))
public class Glossary implements Serializable {

    private static final long serialVersionUID = 1L;

    public Glossary(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique glossary name
     *
     * e.g. project/{project slug}, global/default
     */
    @NotNull
    private String qualifiedName;

    public Glossary() {
    }

    public Long getId() {
        return this.id;
    }

    /**
     * Unique glossary name
     *
     * e.g. project/{project slug}, global/default
     */
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Glossary glossary = (Glossary) o;
        return Objects.equals(qualifiedName, glossary.qualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(qualifiedName);
    }
}
