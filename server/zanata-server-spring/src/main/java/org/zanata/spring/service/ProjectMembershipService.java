package org.zanata.spring.service;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectMember;
import org.zanata.model.ProjectRole;
import org.zanata.spring.repository.PersonRepository;
import org.zanata.spring.repository.ProjectRepository;

/**
 * Service for the Project People tab: add/remove people and grant/revoke
 * project-wide {@link ProjectRole}s. Per-locale {@code LocaleRole} membership
 * is a separate concern (handled by a later locale-team UI pass).
 */
@Service
public class ProjectMembershipService {

    private final ProjectRepository projectRepository;
    private final PersonRepository personRepository;

    public ProjectMembershipService(ProjectRepository projectRepository,
                                    PersonRepository personRepository) {
        this.projectRepository = projectRepository;
        this.personRepository = personRepository;
    }

    /**
     * Replace the project-wide roles for {@code personId} on {@code projectSlug}
     * with the supplied set. Empty set removes the person from the team.
     * A project must always retain at least one Maintainer — the call is
     * rejected if it would clear the last one.
     */
    @Transactional
    public void setProjectRoles(String projectSlug, Long personId, Set<ProjectRole> desired) {
        HProject project = projectRepository.findBySlugWithMembers(projectSlug)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectSlug));
        HPerson person = personRepository.findById(personId)
                .orElseThrow(() -> new IllegalArgumentException("Person not found: " + personId));

        Set<ProjectRole> wanted = desired == null ? EnumSet.noneOf(ProjectRole.class)
                : EnumSet.copyOf(desired);

        // Existing rows for this person
        Set<HProjectMember> mine = project.getMembers().stream()
                .filter(m -> m.getPerson() != null
                        && person.getId().equals(m.getPerson().getId()))
                .collect(java.util.stream.Collectors.toSet());

        EnumSet<ProjectRole> currentRoles = EnumSet.noneOf(ProjectRole.class);
        for (HProjectMember m : mine) currentRoles.add(m.getRole());

        // Safety: ensure we never strand a project without a Maintainer.
        long maintainersAfter = project.getMembers().stream()
                .filter(m -> m.getRole() == ProjectRole.Maintainer)
                .filter(m -> !person.getId().equals(m.getPerson().getId())
                        || wanted.contains(ProjectRole.Maintainer))
                .count();
        if (maintainersAfter == 0) {
            throw new IllegalStateException(
                    "A project must keep at least one Maintainer.");
        }

        // Remove revoked roles
        for (HProjectMember existing : mine) {
            if (!wanted.contains(existing.getRole())) {
                project.getMembers().remove(existing);
            }
        }
        // Add new roles
        for (ProjectRole r : wanted) {
            if (!currentRoles.contains(r)) {
                project.getMembers().add(new HProjectMember(project, person, r));
            }
        }
        projectRepository.save(project);
    }

    /** Convenience overload for the "Add someone" dialog. */
    @Transactional
    public void addMember(String projectSlug, Long personId, Set<ProjectRole> roles) {
        setProjectRoles(projectSlug, personId, roles);
    }
}
