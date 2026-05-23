package org.zanata.spring.web.rest;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.zanata.spring.repository.LocaleRepository;

/**
 * Replaces the legacy LocalesService.  The /languages React screen pulls
 * the full list and is also called for the locale picker on most other
 * screens, so this endpoint matters for the cross-screen UX.
 */
@RestController
@RequestMapping("/rest/locales")
public class LocaleController {

    private final LocaleRepository localeRepository;

    public LocaleController(LocaleRepository localeRepository) {
        this.localeRepository = localeRepository;
    }

    /**
     * Mirrors the legacy LocalesService GET /rest/locales response shape:
     * {totalCount, results: [{localeDetails, memberCount, requestCount}]}.
     * The /languages React screen reads results[i].localeDetails.localeId,
     * displayName, nativeName, etc.
     */
    @GetMapping
    public LocalesResults list(
            @RequestParam(value = "filter", defaultValue = "") String filter,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "10") int sizePerPage) {
        int p = Math.max(1, page) - 1;
        int s = Math.min(Math.max(1, sizePerPage), 200);
        var pageResult = localeRepository.search(
                filter == null ? "" : filter, PageRequest.of(p, s));

        List<LanguageTeamSearchResult> results = pageResult.getContent().stream()
                .map(l -> new LanguageTeamSearchResult(toLocaleDetails(l), 0, 0))
                .collect(Collectors.toList());

        return new LocalesResults((int) pageResult.getTotalElements(), results);
    }

    /** Source locales available for new project versions / glossary entries. */
    @GetMapping("/source")
    public List<LocaleDetails> sourceLocales() {
        return localeRepository.findAll().stream()
                .filter(HLocale::isActive)
                .map(LocaleController::toLocaleDetails)
                .collect(Collectors.toList());
    }

    @GetMapping("/locale/{id}")
    public ResponseEntity<LocaleDetails> byId(@PathVariable("id") String id) {
        return localeRepository.findByLocaleId(new LocaleId(id))
                .map(LocaleController::toLocaleDetails)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static LocaleDetails toLocaleDetails(HLocale l) {
        return new LocaleDetails(
                l.getLocaleId() == null ? null : l.getLocaleId().getId(),
                l.getDisplayName(),
                null, // alias
                l.getNativeName(),
                l.isActive(),
                l.isEnabledByDefault(),
                l.getPluralForms(),
                false); // rtl — not modelled on HLocale today
    }

    public record LocalesResults(int totalCount, List<LanguageTeamSearchResult> results) {}

    public record LanguageTeamSearchResult(LocaleDetails localeDetails,
                                           long memberCount,
                                           long requestCount) {}

    public record LocaleDetails(
            String localeId,
            String displayName,
            String alias,
            String nativeName,
            boolean enabled,
            boolean enabledByDefault,
            String pluralForms,
            boolean rtl) {}
}
