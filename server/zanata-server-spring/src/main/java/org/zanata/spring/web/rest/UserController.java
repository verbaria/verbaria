package org.zanata.spring.web.rest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountOption;
import org.zanata.model.HLocale;
import org.zanata.model.HLocaleMember;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.PersonRepository;
import org.zanata.spring.repository.ProjectRepository;

/**
 * /rest/user endpoints. Exposes the currently authenticated user and
 * basic per-user lookups consumed by the React UI.
 */
@RestController
@RequestMapping("/rest/user")
public class UserController {

    private final PersonRepository personRepository;
    private final AccountRepository accountRepository;
    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;

    public UserController(PersonRepository personRepository,
                          AccountRepository accountRepository,
                          ProjectRepository projectRepository,
                          LocaleRepository localeRepository,
                          LocaleMemberRepository localeMemberRepository) {
        this.personRepository = personRepository;
        this.accountRepository = accountRepository;
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
    }

    /**
     * Returns the currently authenticated user from Spring Security's
     * SecurityContext, joined with the HAccount/HPerson row in the same
     * database. Returns an anonymous placeholder if no one is logged in
     * (the React frontend treats {@code username == null} as the
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
        if ("anonymous".equalsIgnoreCase(username)) {
            return ResponseEntity.ok(new CurrentUser(null, "Anonymous", "", false));
        }
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

    @GetMapping("/permission/glossary")
    @Transactional(readOnly = true)
    public Map<String, Boolean> glossaryPermission(
            @RequestParam(value = "qualifiedName", required = false) String qualifiedName) {
        Authentication auth = currentAuth();
        if (auth == null) {
            return permMap(false);
        }
        if (hasAdmin(auth)) {
            return permMap(true);
        }
        if (qualifiedName != null && qualifiedName.startsWith("project/")) {
            String slug = qualifiedName.substring("project/".length());
            HPerson person = currentPerson(auth).orElse(null);
            if (person != null) {
                Optional<HProject> project = projectRepository.findBySlug(slug);
                if (project.isPresent() && containsPerson(project.get().getMaintainers(), person)) {
                    return permMap(true);
                }
            }
        }
        return permMap(false);
    }

    @GetMapping("/permission/roles/project/{slug}")
    @Transactional(readOnly = true)
    public Map<String, Boolean> projectRoles(
            @PathVariable("slug") String slug,
            @RequestParam(value = "localeId", required = false) String localeId) {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("isMaintainer", false);
        out.put("isReviewer", false);
        out.put("isTranslator", false);

        Authentication auth = currentAuth();
        if (auth == null) {
            return out;
        }
        Optional<HPerson> personOpt = currentPerson(auth);
        if (personOpt.isEmpty()) {
            return out;
        }
        HPerson person = personOpt.get();

        projectRepository.findBySlug(slug).ifPresent(p ->
                out.put("isMaintainer", containsPerson(p.getMaintainers(), person)));

        if (localeId != null && !localeId.isBlank()) {
            HLocale locale = localeRepository
                    .findByLocaleId(new LocaleId(localeId)).orElse(null);
            if (locale != null) {
                localeMemberRepository.findByLocaleAndPerson(locale, person)
                        .ifPresent(m -> {
                            out.put("isReviewer", m.isReviewer());
                            out.put("isTranslator", m.isTranslator());
                        });
            }
        }
        return out;
    }

    /**
     * Webeditor preferences (font size, panel visibility, etc.) returned
     * as a flat key/value map so the React editor's settings loader can
     * hydrate its Redux store.
     */
    @GetMapping("/settings/webeditor")
    @Transactional(readOnly = true)
    public Map<String, Object> webeditorSettings() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("show.suggestions", true);
        defaults.put("show.suggestions.diff", true);
        defaults.put("show.translation.history", true);
        defaults.put("show.glossary", true);
        defaults.put("use.code.mirror.editor", false);
        defaults.put("editor.font.size", "16px");
        defaults.put("enter.saves.translation", true);

        Authentication auth = currentAuth();
        if (auth == null) {
            return defaults;
        }
        HAccount account = accountRepository.findByUsername(auth.getName()).orElse(null);
        if (account == null || account.getEditorOptions() == null) {
            return defaults;
        }
        Map<String, HAccountOption> saved = account.getEditorOptions();
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            HAccountOption opt = saved.get(entry.getKey());
            if (opt == null || opt.getValue() == null) continue;
            if (entry.getValue() instanceof Boolean) {
                defaults.put(entry.getKey(), opt.getValueAsBoolean());
            } else {
                defaults.put(entry.getKey(), opt.getValue());
            }
        }
        return defaults;
    }

    private static Authentication currentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth;
    }

    private static boolean hasAdmin(Authentication auth) {
        return auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Optional<HPerson> currentPerson(Authentication auth) {
        return accountRepository.findByUsername(auth.getName())
                .map(HAccount::getPerson);
    }

    private static boolean containsPerson(Iterable<HPerson> people, HPerson person) {
        if (people == null || person == null || person.getId() == null) return false;
        for (HPerson p : people) {
            if (p != null && person.getId().equals(p.getId())) return true;
        }
        return false;
    }

    private static Map<String, Boolean> permMap(boolean granted) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("insertGlossary", granted);
        m.put("updateGlossary", granted);
        m.put("deleteGlossary", granted);
        return m;
    }

    public record CurrentUser(String username, String name, String email, boolean admin) {}
}
