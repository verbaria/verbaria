package org.zanata.spring.web.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.spring.repository.ProjectRepository;

/**
 * Project-scoped REST endpoints the React UI hits under
 * /rest/projects/p/{slug}/... — read-side queries the glossary screen
 * makes when navigated to /glossary/project/{slug}.
 */
@RestController
@RequestMapping("/rest/projects/p")
public class ProjectsApiController {

    private static final String GLOBAL_QUALIFIED_NAME = "global/default";

    private final ProjectRepository projectRepository;

    public ProjectsApiController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping("/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> project(@PathVariable("slug") String slug) {
        return projectRepository.findBySlug(slug).map(p -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", p.getSlug());
            out.put("name", p.getName() == null ? p.getSlug() : p.getName());
            out.put("description", p.getDescription() == null ? "" : p.getDescription());
            out.put("status", p.getStatus() == null ? "ACTIVE" : p.getStatus().name());
            out.put("defaultType", p.getDefaultProjectType() == null
                    ? "Gettext" : p.getDefaultProjectType().name());
            return ResponseEntity.ok(out);
        }).orElseGet(() -> ResponseEntity.notFound().build());
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
}
