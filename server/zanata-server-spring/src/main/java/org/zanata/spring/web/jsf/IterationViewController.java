package org.zanata.spring.web.jsf;

import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.ProjectRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Thymeleaf rewrite of /iteration/view/{projectSlug}/{versionSlug}.xhtml.
 * The legacy versionHomeAction CDI bean is replaced by a direct repository
 * lookup; the page renders the project breadcrumb, version label, overall
 * statistics placeholder and the Languages / Documents / Groups tab strip.
 */
@Controller
public class IterationViewController {

    private final ProjectRepository projectRepository;

    public IterationViewController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    @GetMapping({
            "/iteration/view/{projectSlug}/{versionSlug}",
            "/iteration/view/{projectSlug}/{versionSlug}/{tab}"
    })
    public String view(@PathVariable("projectSlug") String projectSlug,
                       @PathVariable("versionSlug") String versionSlug,
                       Model model, HttpServletResponse response) throws IOException {
        var match = projectRepository.findAll().stream()
                .filter(p -> projectSlug.equals(p.getSlug()))
                .findFirst();
        if (match.isEmpty()) {
            response.sendError(404, "Project not found: " + projectSlug);
            return null;
        }
        var project = match.get();
        HProjectIteration version = project.getProjectIterations() == null ? null
                : project.getProjectIterations().stream()
                        .filter(v -> versionSlug.equals(v.getSlug()))
                        .findFirst().orElse(null);
        if (version == null) {
            response.sendError(404, "Version not found: " + projectSlug + "/" + versionSlug);
            return null;
        }
        model.addAttribute("project", project);
        model.addAttribute("version", version);
        model.addAttribute("projectSlug", projectSlug);
        model.addAttribute("versionSlug", versionSlug);
        return "iteration/view";
    }
}
