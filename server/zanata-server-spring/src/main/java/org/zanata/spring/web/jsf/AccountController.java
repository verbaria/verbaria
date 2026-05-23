package org.zanata.spring.web.jsf;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Account self-service pages (register, password reset, activate,
 * email validation, OpenID/SSO shortcuts). Login lives in
 * LoginController in this package.
 */
@Controller
@RequestMapping("/account")
public class AccountController {

    @GetMapping({"/register", "/register.xhtml"})
    public String register() { return "account/register"; }

    @PostMapping({"/register", "/register.xhtml"})
    public String doRegister(@RequestParam("username") String username,
                             @RequestParam("email") String email,
                             Model model) {
        model.addAttribute("message",
                "Registration for " + username + " (" + email + ") accepted in dev mode.");
        return "account/register";
    }

    @GetMapping({"/password_reset_request", "/password_reset_request.xhtml"})
    public String passwordResetRequest() { return "account/password_reset_request"; }

    @PostMapping({"/password_reset_request", "/password_reset_request.xhtml"})
    public String doPasswordResetRequest(@RequestParam("usernameEmail") String usernameEmail,
                                         Model model) {
        model.addAttribute("message",
                "Password reset request for '" + usernameEmail + "' acknowledged.");
        return "account/password_reset_request";
    }

    @GetMapping({"/password_reset", "/password_reset.xhtml"})
    public String passwordReset(@RequestParam(value = "key", required = false) String key,
                                Model model) {
        model.addAttribute("key", key);
        return "account/password_reset";
    }

    @PostMapping({"/password_reset", "/password_reset.xhtml"})
    public String doPasswordReset(@RequestParam("password") String password,
                                  @RequestParam("passwordConfirm") String confirm,
                                  Model model) {
        if (!password.equals(confirm)) {
            model.addAttribute("error", "Passwords do not match.");
            return "account/password_reset";
        }
        return "redirect:/account/login";
    }

    @GetMapping({"/logout", "/logout.xhtml"})
    public String logout() { return "account/logout"; }

    @PostMapping({"/logout", "/logout.xhtml"})
    public String doLogout() { return "redirect:/"; }

    @GetMapping({"/activate", "/activate.xhtml"})
    public String activate(@RequestParam(value = "key", required = false) String key,
                           Model model) {
        model.addAttribute("key", key);
        return "account/activate";
    }

    @GetMapping({"/inactive_account", "/inactive_account.xhtml"})
    public String inactive() { return "account/inactive_account"; }

    @GetMapping({"/validate_email", "/validate_email.xhtml", "/email_validation", "/email_validation.xhtml"})
    public String validateEmail(@RequestParam(value = "key", required = false) String key,
                                Model model) {
        model.addAttribute("key", key);
        return "account/validate_email";
    }

    @GetMapping({"/openid", "/openid.xhtml"})
    public String openid() { return "account/openid"; }

    @GetMapping({"/sso", "/ssologin", "/ssologin.xhtml"})
    public String sso() { return "account/sso"; }

    @GetMapping({"/singleopenidlogin", "/singleopenidlogin.xhtml"})
    public String singleOpenid() { return "account/single_openid"; }

    @GetMapping({"/create_user", "/create_user.xhtml"})
    public String createUser() { return "account/create_user"; }
}
