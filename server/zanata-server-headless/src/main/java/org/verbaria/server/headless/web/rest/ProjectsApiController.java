package org.verbaria.server.headless.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.EntityStatus;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.ProjectIteration;
import org.verbaria.server.headless.repository.ProjectRepository;

/**
 * Project-scoped REST endpoints under {@code /rest/projects/p/{slug}/...}.
 * Serves the legacy {@code Project} DTO so the CLI (Accept: application/xml)
 * and the React glossary screen (Accept: application/json) both work
 * through the same JAXB-annotated representation.
 */
@RestController
@RequestMapping("/rest/projects/p")
public class ProjectsApiController {

    private static final String GLOBAL_QUALIFIED_NAME = "global/default";

    private final ProjectRepository projectRepository;

    public ProjectsApiController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping(value = "/{slug}",
            produces = {MediaType.APPLICATION_XML_VALUE,
                        MediaType.APPLICATION_JSON_VALUE,
                        "application/vnd.zanata.project+xml",
                        "application/vnd.zanata.project+json"})
    @Transactional(readOnly = true)
    public ResponseEntity<Project> project(@PathVariable("slug") String slug) {
        return projectRepository.findBySlugWithIterations(slug)
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Returns the qualified glossary name for a project. Every project
     * currently shares the global glossary, so this returns the same
     * constant as /rest/glossary/qualifiedName.
     */
    @GetMapping("/{slug}/glossary/qualifiedName")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> qualifiedName(@PathVariable("slug") String slug) {
        if (projectRepository.findBySlug(slug).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("name", GLOBAL_QUALIFIED_NAME));
    }

    private static Project toDto(HProject p) {
        Project dto = new Project();
        dto.setId(p.getSlug());
        dto.setName(p.getName() == null ? p.getSlug() : p.getName());
        dto.setDescription(p.getDescription());
        dto.setSourceViewURL(p.getResolvedSourceViewURL());
        dto.setStatus(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
        String effectiveType = p.getEffectiveDefaultProjectType();
        dto.setDefaultType(effectiveType == null ? "file" : effectiveType);
        List<ProjectIteration> iters = new ArrayList<>();
        if (p.getProjectIterations() != null) {
            for (HProjectIteration i : p.getProjectIterations()) {
                ProjectIteration ito = new ProjectIteration(i.getSlug());
                ito.setStatus(i.getStatus() == null
                        ? EntityStatus.ACTIVE : i.getStatus());
                if (i.getProjectType() != null) {
                    ito.setProjectType(i.getProjectType());
                }
                iters.add(ito);
            }
        }
        dto.setIterations(iters);
        return dto;
    }
}
