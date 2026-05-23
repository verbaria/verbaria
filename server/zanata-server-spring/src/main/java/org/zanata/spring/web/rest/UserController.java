package org.zanata.spring.web.rest;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.PersonRepository;

/**
 * Minimal /rest/user replacement for the legacy UserResource.  Until the
 * auth migration lands, the "current user" endpoint returns a dev stub
 * so the React UI keeps rendering — pages that need a real user (admin,
 * profile edit) will start working once Spring Security is wired in.
 */
@RestController
@RequestMapping("/rest/user")
public class UserController {

    private final PersonRepository personRepository;
    private final AccountRepository accountRepository;

    public UserController(PersonRepository personRepository,
                          AccountRepository accountRepository) {
        this.personRepository = personRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Returns the currently authenticated user from Spring Security's
     * SecurityContext, joined with the HAccount/HPerson row that lives
     * in the same database.  Returns an anonymous stub if no one is
     * logged in (the React frontend treats `username == null` as the
     * not-signed-in state).
     */
    @GetMapping
    public CurrentUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return new CurrentUser(null, "Anonymous", "", false);
        }
        String username = auth.getName();
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return accountRepository.findByUsername(username)
                .map(a -> new CurrentUser(
                        a.getUsername(),
                        a.getPerson() == null ? a.getUsername() : a.getPerson().getName(),
                        a.getPerson() == null ? "" : a.getPerson().getEmail(),
                        admin))
                .orElse(new CurrentUser(username, username, "", admin));
    }

    @GetMapping("/{username}")
    public ResponseEntity<CurrentUser> byUsername(@PathVariable("username") String username) {
        return accountRepository.findByUsername(username)
                .map(a -> new CurrentUser(
                        a.getUsername(),
                        a.getPerson() == null ? a.getUsername() : a.getPerson().getName(),
                        a.getPerson() == null ? "" : a.getPerson().getEmail(),
                        a.getRoles() != null && a.getRoles().stream()
                                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()))))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Permission probe used by several React screens before they render
     * editable controls.  Until Spring Security lands, return the
     * conservative defaults — read access yes, write access no — so the
     * pages render without erroring but don't expose admin-only buttons.
     */
    @GetMapping("/permission/glossary")
    public Map<String, Boolean> glossaryPermission(
            @RequestParam(value = "qualifiedName", required = false) String qualifiedName) {
        return Map.of(
                "insertGlossary", false,
                "updateGlossary", false,
                "deleteGlossary", false);
    }

    @GetMapping("/permission/roles/project/{slug}")
    public Map<String, Boolean> projectRoles(
            @PathVariable("slug") String slug,
            @RequestParam(value = "localeId", required = false) String localeId) {
        return Map.of(
                "isMaintainer", false,
                "isReviewer", false,
                "isTranslator", false);
    }

    /**
     * Persisted webeditor preferences (font size, panel visibility, etc.).
     * Returned as a flat key/value map so the React editor's settings
     * loader can hydrate its Redux store without crashing.  All toggles
     * default to sensible values; persistence will come once the
     * HAccountOption migration lands.
     */
    @GetMapping("/settings/webeditor")
    public Map<String, Object> webeditorSettings() {
        return Map.of(
                "show.suggestions", true,
                "show.suggestions.diff", true,
                "show.translation.history", true,
                "show.glossary", true,
                "use.code.mirror.editor", false,
                "editor.font.size", "16px",
                "enter.saves.translation", true);
    }

    public record CurrentUser(String username, String name, String email, boolean admin) {}
}
