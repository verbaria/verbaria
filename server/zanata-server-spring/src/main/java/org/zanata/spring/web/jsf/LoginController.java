package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login form: username/password plus the OpenID/Fedora/Yahoo shortcuts.
 */
@Controller
@org.springframework.web.bind.annotation.RequestMapping("/account")
public class LoginController {

    @GetMapping({"/login", "/login.xhtml"})
    public String login(@RequestParam(value = "error", required = false) String error,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "Login failed: " + error);
        }
        return "account/login";
    }

    @PostMapping({"/login", "/login.xhtml"})
    public String doLogin(@RequestParam("username") String username,
                          @RequestParam("password") String password,
                          Model model) {
        if (username == null || username.isBlank()) {
            model.addAttribute("error", "Username is required.");
            return "account/login";
        }
        return "redirect:/?loginAttempt=" + username;
    }
}
