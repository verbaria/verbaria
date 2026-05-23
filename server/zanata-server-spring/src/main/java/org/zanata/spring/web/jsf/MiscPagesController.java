package org.zanata.spring.web.jsf;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.repository.ProjectRepository;

/**
 * Catch-all for smaller routes that don't fit /account or /admin:
 * error pages, /dashboard/*, /language/{slug}, /profile/merge_account.
 */
@Controller
public class MiscPagesController {

    private final ProjectRepository projectRepository;
    private final IterationGroupRepository iterationGroupRepository;
    private final ActivityRepository activityRepository;
    private final AccountRepository accountRepository;

    public MiscPagesController(ProjectRepository projectRepository,
                               IterationGroupRepository iterationGroupRepository,
                               ActivityRepository activityRepository,
                               AccountRepository accountRepository) {
        this.projectRepository = projectRepository;
        this.iterationGroupRepository = iterationGroupRepository;
        this.activityRepository = activityRepository;
        this.accountRepository = accountRepository;
    }

    @GetMapping({"/error/404", "/error/404.xhtml"})
    public String error404() { return "error/notfound"; }

    @GetMapping({"/error/home", "/error/home.xhtml"})
    public String errorHome() { return "error/home"; }

    @GetMapping({"/error/missing_entity", "/error/missing_entity.xhtml"})
    public String missingEntity() { return "error/missing_entity"; }

    @GetMapping({"/error/viewexpired", "/error/viewexpiredexception.xhtml", "/error/viewexpiredexception"})
    public String viewExpired() { return "error/viewexpired"; }

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

    @GetMapping("/dashboard/activity")
    public String dashboardActivity(Model model) {
        String username = currentUsername();
        model.addAttribute("activities", username == null
                ? java.util.List.of()
                : activityRepository.findByActor(username, PageRequest.of(0, 20)));
        return "dashboard/activity";
    }

    @GetMapping("/dashboard/groups")
    public String dashboardGroups(Model model) {
        model.addAttribute("groups",
                iterationGroupRepository.search("", PageRequest.of(0, 50)).getContent());
        return "dashboard/groups";
    }

    @GetMapping("/dashboard/settings")
    public String dashboardSettings(Model model) {
        String username = currentUsername();
        accountRepository.findByUsername(username == null ? "" : username)
                .ifPresent(a -> {
                    model.addAttribute("account", a);
                    model.addAttribute("person", a.getPerson());
                });
        return "dashboard/settings";
    }

    @GetMapping({"/language/{slug}", "/language/{slug}.xhtml"})
    public String language(@PathVariable("slug") String slug, Model model) {
        model.addAttribute("slug", slug);
        return "language/view";
    }

    @GetMapping({"/profile/merge_account", "/profile/merge_account.xhtml"})
    public String mergeAccount() { return "profile/merge_account"; }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
