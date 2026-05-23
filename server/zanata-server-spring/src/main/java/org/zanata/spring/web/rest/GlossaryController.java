package org.zanata.spring.web.rest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.LocaleId;
import org.zanata.model.HGlossaryEntry;
import org.zanata.model.HGlossaryTerm;
import org.zanata.model.HLocale;
import org.zanata.spring.repository.GlossaryEntryRepository;
import org.zanata.spring.repository.LocaleRepository;

/**
 * Backs the React /glossary screen.  Queries HGlossaryEntry / HGlossaryTerm
 * directly via Spring Data; replaces the legacy GlossaryService for the
 * read-side endpoints the UI calls.  CRUD/upload still need porting.
 */
@RestController
@RequestMapping("/rest/glossary")
public class GlossaryController {

    private static final String GLOBAL_QUALIFIED_NAME = "global/default";

    private final LocaleRepository localeRepository;
    private final GlossaryEntryRepository glossaryRepository;

    public GlossaryController(LocaleRepository localeRepository,
                              GlossaryEntryRepository glossaryRepository) {
        this.localeRepository = localeRepository;
        this.glossaryRepository = glossaryRepository;
    }

    @GetMapping("/qualifiedName")
    public QualifiedName qualifiedName() {
        return new QualifiedName(GLOBAL_QUALIFIED_NAME);
    }

    @GetMapping("/info")
    @Transactional(readOnly = true)
    public GlossaryInfo info(
            @RequestParam(value = "qualifiedName", defaultValue = GLOBAL_QUALIFIED_NAME)
                    String qualifiedName) {
        List<HLocale> locales = localeRepository.findAll();
        HLocale src = locales.stream()
                .filter(l -> l.getLocaleId() != null
                        && "en-US".equals(l.getLocaleId().getId()))
                .findFirst()
                .orElseGet(() -> locales.isEmpty() ? null : locales.get(0));

        long srcCount = src == null ? 0
                : glossaryRepository.countBySourceLocale(src.getLocaleId(), qualifiedName);
        GlossaryLocaleInfo srcInfo = src == null ? null
                : new GlossaryLocaleInfo(toDetails(src.getLocaleId()), (int) srcCount);

        Map<HLocale, Long> transCounts = src == null ? Map.of()
                : glossaryRepository
                        .countTranslationsBySourceLocale(src.getLocaleId(), qualifiedName)
                        .stream()
                        .collect(Collectors.toMap(
                                row -> (HLocale) row[0],
                                row -> (Long) row[1],
                                Long::sum));

        List<GlossaryLocaleInfo> targets = locales.stream()
                .filter(l -> src == null || !l.getLocaleId().equals(src.getLocaleId()))
                .map(l -> new GlossaryLocaleInfo(toDetails(l.getLocaleId()),
                        transCounts.getOrDefault(l, 0L).intValue()))
                .collect(Collectors.toList());

        return new GlossaryInfo(srcInfo, targets);
    }

    @GetMapping("/entries")
    @Transactional(readOnly = true)
    public GlossaryResults entries(
            @RequestParam(value = "srcLocale", defaultValue = "en-US") String srcLocale,
            @RequestParam(value = "transLocale", required = false) String transLocale,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "sizePerPage", defaultValue = "1000") int sizePerPage,
            @RequestParam(value = "filter", required = false) String filter,
            @RequestParam(value = "qualifiedName", defaultValue = GLOBAL_QUALIFIED_NAME)
                    String qualifiedName) {

        LocaleId src = new LocaleId(srcLocale);
        long total = glossaryRepository.countBySourceLocale(src, qualifiedName);
        if (total == 0) {
            return new GlossaryResults(0, List.of());
        }
        int offset = Math.max(0, page - 1) * Math.max(1, sizePerPage);
        var pageable = PageRequest.of(offset / Math.max(1, sizePerPage),
                Math.max(1, sizePerPage));
        List<HGlossaryEntry> rows = glossaryRepository.findBySourceLocale(
                src, qualifiedName, pageable);

        String needle = filter == null ? null : filter.trim().toLowerCase();
        List<GlossaryEntry> mapped = new ArrayList<>(rows.size());
        for (HGlossaryEntry row : rows) {
            HGlossaryTerm srcTerm = termForLocale(row, row.getSrcLocale());
            if (needle != null && !needle.isEmpty()) {
                String content = srcTerm == null ? "" : srcTerm.getContent();
                if (content == null || !content.toLowerCase().contains(needle)) {
                    continue;
                }
            }
            List<GlossaryTerm> terms = row.getGlossaryTerms().values().stream()
                    .map(t -> new GlossaryTerm(
                            t.getLocale() == null ? null : t.getLocale().getLocaleId().getId(),
                            t.getContent(),
                            t.getComment(),
                            t.getLastChanged() == null ? null : t.getLastChanged().toString(),
                            t.getLastModifiedBy() == null ? null
                                    : t.getLastModifiedBy().getAccount() == null
                                            ? null
                                            : t.getLastModifiedBy().getAccount().getUsername()))
                    .collect(Collectors.toList());
            mapped.add(new GlossaryEntry(
                    row.getId(),
                    row.getPos(),
                    row.getDescription(),
                    row.getSourceRef(),
                    row.getSrcLocale() == null ? null
                            : row.getSrcLocale().getLocaleId().getId(),
                    terms.size(),
                    terms));
        }
        return new GlossaryResults((int) total, mapped);
    }

    private static HGlossaryTerm termForLocale(HGlossaryEntry entry, HLocale locale) {
        if (locale == null) return null;
        return entry.getGlossaryTerms().entrySet().stream()
                .filter(e -> Objects.equals(e.getKey(), locale))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private static LocaleController.LocaleDetails toDetails(LocaleId id) {
        return new LocaleController.LocaleDetails(
                id == null ? null : id.getId(), null, null, null,
                true, false, null, false);
    }

    public record QualifiedName(String name) {}
    public record GlossaryLocaleInfo(LocaleController.LocaleDetails locale, int numberOfTerms) {}
    public record GlossaryInfo(GlossaryLocaleInfo srcLocale, List<GlossaryLocaleInfo> transLocale) {}
    public record GlossaryResults(int totalCount, List<GlossaryEntry> results) {}
    public record GlossaryEntry(Long id, String pos, String description,
                                String sourceReference, String srcLang,
                                int termsCount, List<GlossaryTerm> glossaryTerms) {}
    public record GlossaryTerm(String locale, String content, String comment,
                               String lastModifiedDate, String lastModifiedBy) {}
}
