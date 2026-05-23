package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.zanata.spring.repository.PersonRepository;

/**
 * Landing pages for the /admin/* routes.
 */
@Controller
@RequestMapping("/admin")
public class AdminPagesController {

    private final PersonRepository personRepository;

    public AdminPagesController(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @GetMapping({"/usermanager", "/usermanager.xhtml"})
    public String userManager(Model model) {
        model.addAttribute("count", personRepository.count());
        return "admin/usermanager";
    }

    @GetMapping({"/userdetail", "/userdetail.xhtml"})
    public String userDetail(@org.springframework.web.bind.annotation.RequestParam(value="username", required=false) String username,
                             Model model) {
        model.addAttribute("username", username);
        return "admin/userdetail";
    }

    @GetMapping({"/rolemanager", "/rolemanager.xhtml"})
    public String roleManager() { return "admin/rolemanager"; }

    @GetMapping({"/roledetail", "/roledetail.xhtml"})
    public String roleDetail(@org.springframework.web.bind.annotation.RequestParam(value="role", required=false) String role,
                             Model model) {
        model.addAttribute("role", role);
        return "admin/roledetail";
    }

    @GetMapping({"/rolerules", "/rolerules.xhtml"})
    public String roleRules() { return "admin/rolerules"; }

    @GetMapping({"/roleruledetails", "/roleruledetails.xhtml"})
    public String roleRuleDetails() { return "admin/roleruledetails"; }

    @GetMapping({"/search", "/search.xhtml"})
    public String search() { return "admin/search"; }

    @GetMapping({"/stats", "/stats.xhtml"})
    public String stats() { return "admin/stats"; }

    @GetMapping({"/monitoring", "/monitoring.xhtml"})
    public String monitoring() { return "admin/monitoring"; }

    @GetMapping({"/cachestats", "/cachestats.xhtml"})
    public String cacheStats() { return "admin/cachestats"; }

    @GetMapping({"/processmanager", "/processmanager.xhtml"})
    public String processManager() { return "admin/processmanager"; }
}
