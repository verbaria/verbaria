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

import static org.zanata.security.EntityAction.DELETE;
import static org.zanata.security.EntityAction.INSERT;
import static org.zanata.security.EntityAction.UPDATE;
import static org.zanata.model.ProjectRole.Maintainer;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.google.common.collect.ImmutableSet;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.validator.constraints.NotEmpty;
import org.zanata.annotation.EntityRestrict;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.model.validator.Url;
import org.zanata.rest.dto.Project;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.leangen.graphql.annotations.types.GraphQLType;

/**
 * @see Project
 */
@Entity
@Cacheable
@Access(AccessType.FIELD)
@EntityRestrict({ INSERT, UPDATE, DELETE })
@GraphQLType(name = "Project")
public class HProject extends SlugEntityBase
        implements Serializable, HasEntityStatus, HasUserFriendlyToString {

    private static final long serialVersionUID = 1L;
    @Size(max = 80)
    @NotEmpty
    private String name;
    @Size(max = 100)
    private String description;
    @jakarta.persistence.Lob
    private String homeContent;
    @Url(canEndInSlash = true)
    @Column(columnDefinition = "text")
    private String sourceViewURL;
    @Column(columnDefinition = "text")
    private String sourceCheckoutURL;
    private boolean overrideLocales = false;
    private boolean restrictedByRoles = false;
    private boolean privateProject = false;
    @OneToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_copy_trans_opts_id")
    private HCopyTransOptions defaultCopyTransOpts;
    @ManyToMany
    @JoinTable(name = "HProject_Locale",
            joinColumns = @JoinColumn(name = "projectId"),
            inverseJoinColumns = @JoinColumn(name = "localeId"))
    private Set<HLocale> customizedLocales = Sets.newHashSet();
    @ElementCollection
    @JoinTable(name = "HProject_LocaleAlias",
            joinColumns = { @JoinColumn(name = "projectId") })
    @MapKeyColumn(name = "localeId")
    @Column(name = "alias", nullable = false)
    private Map<LocaleId, String> localeAliases = Maps.newHashMap();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "project",
            orphanRemoval = true)
    private List<WebHook> webHooks = Lists.newArrayList();
    @Enumerated(EnumType.STRING)
    private ProjectType defaultProjectType;
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "default_source_locale_id")
    private HLocale defaultSourceLocale;

    public HLocale getDefaultSourceLocale() {
        return defaultSourceLocale;
    }

    public void setDefaultSourceLocale(HLocale defaultSourceLocale) {
        this.defaultSourceLocale = defaultSourceLocale;
    }

    /**
     * Immutable set of maintainers for this project.
     *
     * To change maintainers, use other methods in this class.
     *
     * @see #addMaintainer(HPerson)
     * @see #removeMaintainer(HPerson)
     */
    @Transient
    public ImmutableSet<HPerson> getMaintainers() {
        return ImmutableSet.copyOf(members.stream()
                .filter(pm -> pm.getRole() == Maintainer)
                .map(HProjectMember::getPerson)
                .iterator());
    }

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "project",
            orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<HProjectMember> members = Sets.newHashSet();
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "project",
            orphanRemoval = true)
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private Set<HProjectLocaleMember> localeMembers = Sets.newHashSet();
    @ManyToMany
    @JoinTable(name = "HProject_AllowedRole",
            joinColumns = @JoinColumn(name = "projectId"),
            inverseJoinColumns = @JoinColumn(name = "roleId"))
    private Set<HAccountRole> allowedRoles = Sets.newHashSet();
    @ElementCollection
    @JoinTable(name = "HProject_Validation",
            joinColumns = { @JoinColumn(name = "projectId") })
    @MapKeyColumn(name = "validation")
    @Column(name = "state", nullable = false)
    private Map<String, String> customizedValidations = Maps.newHashMap();
    @OneToMany(mappedBy = "project")
    @Cache(usage = CacheConcurrencyStrategy.TRANSACTIONAL)
    private List<HProjectIteration> projectIterations = Lists.newArrayList();
        @NotNull

    private EntityStatus status = EntityStatus.ACTIVE;

    public void addIteration(HProjectIteration iteration) {
        projectIterations.add(iteration);
        iteration.setProject(this);
    }

    /**
     * Add a maintainer to this project.
     *
     * @param maintainer
     *            person to add as a maintainer
     * @see {@link #getMaintainers}
     */
    public void addMaintainer(HPerson maintainer) {
        getMembers().add(new HProjectMember(this, maintainer, Maintainer));
    }

    /**
     * Remove a maintainer from this project.
     *
     * @param maintainer
     *            person to remove as a maintainer
     * @see {@link #getMaintainers}
     */
    public void removeMaintainer(HPerson maintainer) {
        // business rule: every project must have at least one maintainer
        // No need to check whether the person is the actual last maintainer. If
        // there is only one maintainer then removal of any other person would
        // do nothing anyway.
        if (getMaintainers().size() > 1) {
            getMembers()
                    .remove(new HProjectMember(this, maintainer, Maintainer));
        }
    }

    @Override
    public String userFriendlyToString() {
        return String.format("Project(name=%s, slug=%s, status=%s)", getName(),
                getSlug(), getStatus());
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof HProject))
            return false;
        final HProject other = (HProject) o;
        if (!other.canEqual((Object) this))
            return false;
        if (!super.equals(o))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof HProject;
    }

    @Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + super.hashCode();
        return result;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setHomeContent(final String homeContent) {
        this.homeContent = homeContent;
    }

    public void setSourceViewURL(final String sourceViewURL) {
        this.sourceViewURL = sourceViewURL;
    }

    public void setSourceCheckoutURL(final String sourceCheckoutURL) {
        this.sourceCheckoutURL = sourceCheckoutURL;
    }

    public void setOverrideLocales(final boolean overrideLocales) {
        this.overrideLocales = overrideLocales;
    }

    public void setRestrictedByRoles(final boolean restrictedByRoles) {
        this.restrictedByRoles = restrictedByRoles;
    }

    public void setPrivateProject(final boolean privateProject) {
        this.privateProject = privateProject;
    }

    public void setDefaultCopyTransOpts(
            final HCopyTransOptions defaultCopyTransOpts) {
        this.defaultCopyTransOpts = defaultCopyTransOpts;
    }

    public void setCustomizedLocales(final Set<HLocale> customizedLocales) {
        this.customizedLocales = customizedLocales;
    }

    public void setLocaleAliases(final Map<LocaleId, String> localeAliases) {
        this.localeAliases = localeAliases;
    }

    public void setWebHooks(final List<WebHook> webHooks) {
        this.webHooks = webHooks;
    }

    public void setDefaultProjectType(final ProjectType defaultProjectType) {
        this.defaultProjectType = defaultProjectType;
    }

    public void setMembers(final Set<HProjectMember> members) {
        this.members = members;
    }

    public void
            setLocaleMembers(final Set<HProjectLocaleMember> localeMembers) {
        this.localeMembers = localeMembers;
    }

    public void setAllowedRoles(final Set<HAccountRole> allowedRoles) {
        this.allowedRoles = allowedRoles;
    }

    public void setCustomizedValidations(
            final Map<String, String> customizedValidations) {
        this.customizedValidations = customizedValidations;
    }

    public void setProjectIterations(
            final List<HProjectIteration> projectIterations) {
        this.projectIterations = projectIterations;
    }

    public void setStatus(final EntityStatus status) {
        this.status = status;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public String getHomeContent() {
        return this.homeContent;
    }

    public String getSourceViewURL() {
        return this.sourceViewURL;
    }

    public String getSourceCheckoutURL() {
        return this.sourceCheckoutURL;
    }

    public boolean isOverrideLocales() {
        return this.overrideLocales;
    }

    public boolean isRestrictedByRoles() {
        return this.restrictedByRoles;
    }

    public boolean isPrivateProject() {
        return this.privateProject;
    }

    public HCopyTransOptions getDefaultCopyTransOpts() {
        return this.defaultCopyTransOpts;
    }

    public Set<HLocale> getCustomizedLocales() {
        return this.customizedLocales;
    }

    public Map<LocaleId, String> getLocaleAliases() {
        return this.localeAliases;
    }

    public List<WebHook> getWebHooks() {
        return this.webHooks;
    }

    @Nullable
    public ProjectType getDefaultProjectType() {
        return this.defaultProjectType;
    }

    public Set<HProjectMember> getMembers() {
        return this.members;
    }

    public Set<HProjectLocaleMember> getLocaleMembers() {
        return this.localeMembers;
    }

    public Set<HAccountRole> getAllowedRoles() {
        return this.allowedRoles;
    }

    public Map<String, String> getCustomizedValidations() {
        return this.customizedValidations;
    }

    public List<HProjectIteration> getProjectIterations() {
        return this.projectIterations;
    }

    @Override
    public EntityStatus getStatus() {
        return this.status;
    }

    @Override
    public String toString() {
        return "HProject(super=" + super.toString() + ", name=" + this.getName()
                + ")";
    }
}
