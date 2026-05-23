package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.zanata.spring.repository.ProjectRepository;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Renders /project/view/{slug}: the project home page with links to
 * versions, people, about and settings tabs.
 */
@Controller
public class ProjectViewController {

    private final ProjectRepository projectRepository;

    public ProjectViewController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping({
            "/project/view/{slug}",
            "/project/view/{slug}/versions",
            "/project/view/{slug}/people",
            "/project/view/{slug}/about",
            "/project/view/{slug}/settings",
            "/project/view/{slug}/settings/{section}"
    })
    public String view(@PathVariable("slug") String slug, Model model,
                       HttpServletResponse response) throws IOException {
        var match = projectRepository.findBySlug(slug);
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
