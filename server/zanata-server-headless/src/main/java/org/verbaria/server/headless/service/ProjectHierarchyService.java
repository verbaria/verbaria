package org.verbaria.server.headless.service;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.EntityStatus;
import org.zanata.common.MessageEvaluateType;
import org.zanata.common.ProjectType;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;

/**
 * Owns the project parent/child graph: a child inherits its parent's settings,
 * and gets a mirror of the parent's versions so push/pull never 404s on a
 * missing iteration. The hierarchy is exactly one level deep — a parent may not
 * itself be a child, and a child may not have children (no
 * parent&rarr;parent&rarr;child chains).
 */
@Service
public class ProjectHierarchyService {

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;

    public ProjectHierarchyService(ProjectRepository projectRepository,
            ProjectIterationRepository iterationRepository) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
    }

    /** Why a parent choice was refused under the one-level rule. */
    public enum LinkError {
        SELF, PARENT_IS_CHILD, CHILD_HAS_CHILDREN
    }

    /**
     * A detached snapshot of what a child would inherit from {@code parentSlug}
     * — safe to read from the UI after the request transaction closes. Used to
     * preview inherited values (parent set) and to copy them down (parent
     * removed).
     */
    public record EffectiveSettings(ProjectType type, HLocale sourceLocale,
            Set<HLocale> locales, String sourceUrlTemplate,
            MessageEvaluateType messageFormat) {}

    /**
     * Validate a parent choice. Returns the offending {@link LinkError}, or
     * {@code null} when the link is allowed (clearing the parent always is).
     */
    public LinkError validate(HProject child, HProject parent) {
        if (parent == null) {
            return null;
        }
        if (child.getId() != null && child.getId().equals(parent.getId())) {
            return LinkError.SELF;
        }
        if (parent.getParentProject() != null) {
            return LinkError.PARENT_IS_CHILD;
        }
        if (child.getId() != null
                && !projectRepository.findByParentProjectId(child.getId()).isEmpty()) {
            return LinkError.CHILD_HAS_CHILDREN;
        }
        return null;
    }

    @Transactional(readOnly = true)
    public EffectiveSettings effectiveSettings(String parentSlug) {
        HProject parent = projectRepository.findBySlug(parentSlug).orElseThrow();
        Set<HLocale> eff = parent.getEffectiveCustomizedLocales();
        return new EffectiveSettings(
                parent.getEffectiveDefaultProjectType(),
                parent.getEffectiveDefaultSourceLocale(),
                eff == null ? Set.of() : new HashSet<>(eff),
                parent.getEffectiveSourceViewURL(),
                parent.getEffectiveMessageEvaluateType());
    }

    /**
     * Set (or clear) a project's parent and mirror the parent's versions onto
     * it. Throws {@link IllegalArgumentException} when the one-level rule is
     * violated — callers should {@link #validate} first for a friendly message.
     */
    @Transactional
    public void linkParent(HProject child, HProject parent) {
        LinkError err = validate(child, parent);
        if (err != null) {
            throw new IllegalArgumentException("Invalid parent: " + err);
        }
        child.setParentProject(parent);
        projectRepository.save(child);
        if (parent != null) {
            mirrorVersionsFromParent(child, parent);
        }
    }

    /** Create every non-obsolete parent version the child is missing. */
    @Transactional
    public void mirrorVersionsFromParent(HProject child, HProject parent) {
        for (HProjectIteration pv : iterationRepository
                .findForSettingsByProject(parent.getSlug())) {
            createVersionIfMissing(child, pv.getSlug());
        }
    }

    /**
     * Propagate a newly created parent version to all its children. Flat
     * hierarchy — children have no children, so no recursion.
     */
    @Transactional
    public void propagateVersionToChildren(HProject parent, String versionSlug) {
        if (parent.getId() == null) {
            return;
        }
        for (HProject child : projectRepository.findByParentProjectId(parent.getId())) {
            createVersionIfMissing(child, versionSlug);
        }
    }

    private void createVersionIfMissing(HProject child, String versionSlug) {
        if (iterationRepository.findByProjectAndSlug(child.getSlug(), versionSlug)
                .isPresent()) {
            return;
        }
        HProjectIteration v = new HProjectIteration();
        v.setSlug(versionSlug);
        v.setStatus(EntityStatus.ACTIVE);
        v.setProject(child);
        iterationRepository.save(v);
    }
}
