package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.zanata.spring.repository.ProjectRepository;

/**
 * Catch-all for the smaller legacy JSF routes that don't fit /account or
 * /admin: error pages, /dashboard/home, /language/{slug}, /profile/merge_account.
 */
@Controller
public class MiscPagesController {

    private final ProjectRepository projectRepository;

    public MiscPagesController(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    // ─── /error/*.xhtml ─────────────────────────────────────────────────
    @GetMapping({"/error/404", "/error/404.xhtml"})
    public String error404() { return "error/notfound"; }

    @GetMapping({"/error/home", "/error/home.xhtml"})
    public String errorHome() { return "error/home"; }

    @GetMapping({"/error/missing_entity", "/error/missing_entity.xhtml"})
    public String missingEntity() { return "error/missing_entity"; }

    @GetMapping({"/error/viewexpired", "/error/viewexpiredexception.xhtml", "/error/viewexpiredexception"})
    public String viewExpired() { return "error/viewexpired"; }

    // ─── /dashboard/home.xhtml ──────────────────────────────────────────
    @GetMapping({"/dashboard", "/dashboard/", "/dashboard/home", "/dashboard/home.xhtml"})
    public String dashboard(Model model) {
        model.addAttribute("projectCount", projectRepository.count());
        return "dashboard/home";
    }

    @GetMapping("/dashboard/projects")
    public String dashboardProjects(Model model) {
        model.addAttribute("projects", projectRepository.findAll());
        return "dashboard/projects";
    }

    // ─── /language/{slug}.xhtml ─────────────────────────────────────────
    @GetMapping({"/language/{slug}", "/language/{slug}.xhtml"})
    public String language(@PathVariable("slug") String slug, Model model) {
        model.addAttribute("slug", slug);
        return "language/view";
    }

    // ─── /profile/merge_account.xhtml ──────────────────────────────────
    @GetMapping({"/profile/merge_account", "/profile/merge_account.xhtml"})
    public String mergeAccount() { return "profile/merge_account"; }
}
