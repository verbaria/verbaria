package org.zanata.spring.web.rest;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.model.HLocale;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.web.rest.LocaleController.LocaleDetails;

/**
 * Backs the React project-version pages (/project/{slug}/version/{v}).
 * Returns the locales actually enabled for the iteration (respecting the
 * iteration's overrideLocales toggle, falling back to the project's
 * customisation, and finally to all enabled HLocales).
 */
@RestController
public class ProjectController {

    private final LocaleRepository localeRepository;
    private final ProjectIterationRepository iterationRepository;

    public ProjectController(LocaleRepository localeRepository,
                             ProjectIterationRepository iterationRepository) {
        this.localeRepository = localeRepository;
        this.iterationRepository = iterationRepository;
    }

    @GetMapping("/rest/project/{slug}/version/{version}/locales")
    @Transactional(readOnly = true)
    public List<LocaleDetails> supportedLocales(
            @PathVariable("slug") String slug,
            @PathVariable("version") String version) {
        return localesFor(slug, version);
    }

    /**
     * React TMXExportModal expects an array (calls `.map` on the response),
     * so even though a project version has one source locale today we
     * return it as a single-element list — matches the React shape.
     */
    @GetMapping("/rest/projects/p/{slug}/iterations/i/{version}/locales/source")
    @Transactional(readOnly = true)
    public List<LocaleDetails> sourceLocale(
            @PathVariable("slug") String slug,
            @PathVariable("version") String version) {
        List<LocaleDetails> all = localesFor(slug, version);
        return all.stream()
                .filter(l -> "en-US".equals(l.localeId()))
                .findFirst()
                .map(List::of)
                .orElseGet(() -> all.isEmpty() ? List.of() : List.of(all.get(0)));
    }

    private List<LocaleDetails> localesFor(String slug, String version) {
        HProjectIteration iter = iterationRepository
                .findByProjectAndSlug(slug, version).orElse(null);
        Set<HLocale> picked = null;
        if (iter != null) {
            if (iter.isOverrideLocales() && !iter.getCustomizedLocales().isEmpty()) {
                picked = iter.getCustomizedLocales();
            } else if (iter.getProject() != null
                    && iter.getProject().isOverrideLocales()
                    && !iter.getProject().getCustomizedLocales().isEmpty()) {
                picked = iter.getProject().getCustomizedLocales();
            }
        }
        List<HLocale> source = picked != null
                ? picked.stream().collect(Collectors.toList())
                : localeRepository.findAll().stream()
                        .filter(HLocale::isActive)
                        .filter(HLocale::isEnabledByDefault)
                        .collect(Collectors.toList());

        return source.stream()
                .filter(l -> l.getLocaleId() != null)
                .sorted(Comparator.comparing(l -> l.getLocaleId().getId()))
                .map(l -> new LocaleDetails(
                        l.getLocaleId().getId(),
                        l.getDisplayName(),
                        null,
                        l.getNativeName(),
                        l.isActive(),
                        l.isEnabledByDefault(),
                        null,
                        false))
                .collect(Collectors.toList());
    }
}
