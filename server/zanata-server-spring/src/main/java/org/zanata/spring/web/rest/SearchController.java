package org.zanata.spring.web.rest;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.model.HIterationGroup;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.PersonRepository;
import org.zanata.spring.repository.ProjectRepository;

/**
 * Search endpoints driven by the React /explore screen:
 *   GET /rest/search/projects?q=...
 *   GET /rest/search/groups?q=...
 *   GET /rest/search/people?q=...
 *   GET /rest/search/teams/language?q=...
 */
@RestController
@RequestMapping("/rest/search")
public class SearchController {

    private final ProjectRepository projectRepository;
    private final IterationGroupRepository groupRepository;
    private final PersonRepository personRepository;
    private final LocaleRepository localeRepository;

    public SearchController(ProjectRepository projectRepository,
                            IterationGroupRepository groupRepository,
                            PersonRepository personRepository,
                            LocaleRepository localeRepository) {
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.personRepository = personRepository;
        this.localeRepository = localeRepository;
    }

    @GetMapping("/projects")
    @Transactional(readOnly = true)
    public SearchResults searchProjects(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "20") int sizePerPage,
            @RequestParam(value = "includeVersion", defaultValue = "false") boolean includeVersion) {

        Page<HProject> projects = projectRepository.search(safe(q), pageReq(page, sizePerPage));

        List<ProjectSearchResult> results = projects.getContent().stream()
                .map(p -> new ProjectSearchResult(
                        p.getSlug(),
                        p.getName(),
                        p.getDescription(),
                        p.getStatus() == null ? null : p.getStatus().name(),
                        includeVersion ? versionsFor(p) : null))
                .collect(Collectors.toList());

        return new SearchResults((int) projects.getTotalElements(), results, "Project");
    }

    private static List<ProjectVersionSearchResult> versionsFor(HProject p) {
        if (p.getProjectIterations() == null) return List.of();
        return p.getProjectIterations().stream()
                .map(i -> new ProjectVersionSearchResult(
                        i.getSlug(),
                        i.getStatus() == null ? null : i.getStatus().name()))
                .collect(Collectors.toList());
    }

    @GetMapping("/groups")
    public SearchResults searchGroups(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "20") int sizePerPage) {

        Page<HIterationGroup> groups = groupRepository.search(safe(q), pageReq(page, sizePerPage));

        List<GroupSearchResult> results = groups.getContent().stream()
                .map(g -> new GroupSearchResult(
                        g.getSlug(),
                        g.getName(),
                        g.getDescription(),
                        // HIterationGroup has no top-level status field; the
                        // entity uses obsolete-flagging via Slug; report
                        // ACTIVE so the React badge renders.
                        "ACTIVE"))
                .collect(Collectors.toList());

        return new SearchResults((int) groups.getTotalElements(), results, "Group");
    }

    @GetMapping("/people")
    public SearchResults searchPeople(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "20") int sizePerPage) {

        Page<HPerson> people = personRepository.search(safe(q), pageReq(page, sizePerPage));

        List<PersonSearchResult> results = people.getContent().stream()
                .map(p -> new PersonSearchResult(
                        p.getAccount() == null ? null : p.getAccount().getUsername(),
                        p.getName(),
                        p.getEmail()))
                .collect(Collectors.toList());

        return new SearchResults((int) people.getTotalElements(), results, "Person");
    }

    @GetMapping("/teams/language")
    public SearchResults searchLanguageTeams(
            @RequestParam(value = "q", defaultValue = "") String q,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "20") int sizePerPage) {

        Page<HLocale> locales = localeRepository.search(safe(q), pageReq(page, sizePerPage));

        List<LanguageTeamSearchResult> results = locales.getContent().stream()
                .map(l -> new LanguageTeamSearchResult(
                        l.getLocaleId() == null ? null : l.getLocaleId().getId(),
                        l.getDisplayName(),
                        l.getNativeName()))
                .collect(Collectors.toList());

        return new SearchResults((int) locales.getTotalElements(), results, "LanguageTeam");
    }

    private static String safe(String q)  { return q == null ? "" : q; }
    private static PageRequest pageReq(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return PageRequest.of(safePage - 1, safeSize);
    }

    public record SearchResults(int totalCount, List<?> results, String type) {}

    public record ProjectSearchResult(
            String id,
            String title,
            String description,
            String status,
            List<ProjectVersionSearchResult> versions) {}

    public record ProjectVersionSearchResult(String id, String status) {}

    public record GroupSearchResult(String id, String title, String description, String status) {}

    public record PersonSearchResult(String username, String name, String email) {}

    public record LanguageTeamSearchResult(String localeId, String displayName, String nativeName) {}
}
