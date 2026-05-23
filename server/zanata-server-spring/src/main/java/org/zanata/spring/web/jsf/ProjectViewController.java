package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.zanata.spring.repository.ProjectRepository;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Thymeleaf rewrite of the legacy /project/view/{slug}.xhtml page.
 *
 * Replaces the JSF "projectHome" backing-bean + RichFaces tabbed layout
 * with a flat read-only page that the new Spring Boot server can render
 * without dragging the Faces / Weld / DeltaSpike stack in.  Editable
 * tabs (settings, people, languages) come later — this is the
 * read-mostly home page that links into them.
 */
@Controller
public class ProjectViewController {

    private final ProjectRepository projectRepository;

    public ProjectViewController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping("/project/view/{slug}")
    public String view(@PathVariable("slug") String slug, Model model,
                       HttpServletResponse response) throws IOException {
        var match = projectRepository.findAll().stream()
                .filter(p -> slug.equals(p.getSlug()))
                .findFirst();
        if (match.isEmpty()) {
            response.sendError(404, "Project not found: " + slug);
            return null;
        }
        var project = match.get();
        model.addAttribute("project", project);
        model.addAttribute("slug", slug);
        return "project/view";
    }
}
