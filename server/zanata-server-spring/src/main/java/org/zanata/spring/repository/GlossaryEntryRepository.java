package org.zanata.spring.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HGlossaryEntry;
import org.zanata.model.HLocale;

@Repository
public interface GlossaryEntryRepository
        extends JpaRepository<HGlossaryEntry, Long> {

    @Query("""
            select e from HGlossaryEntry e
            where e.srcLocale.localeId = :srcLocale
              and e.glossary.qualifiedName = :qualifiedName
            """)
    List<HGlossaryEntry> findBySourceLocale(@Param("srcLocale") LocaleId srcLocale,
                                            @Param("qualifiedName") String qualifiedName,
                                            Pageable pageable);

    @Query("""
            select count(e) from HGlossaryEntry e
            where e.srcLocale.localeId = :srcLocale
              and e.glossary.qualifiedName = :qualifiedName
            """)
    long countBySourceLocale(@Param("srcLocale") LocaleId srcLocale,
                             @Param("qualifiedName") String qualifiedName);

    @Query("""
            select t.locale, count(t) from HGlossaryTerm t
            where t.locale.localeId <> t.glossaryEntry.srcLocale.localeId
              and t.glossaryEntry.srcLocale.localeId = :srcLocale
              and t.glossaryEntry.glossary.qualifiedName = :qualifiedName
            group by t.locale
            """)
    List<Object[]> countTranslationsBySourceLocale(@Param("srcLocale") LocaleId srcLocale,
                                                   @Param("qualifiedName") String qualifiedName);

    default long translationCount(HLocale srcLocale, String qualifiedName, HLocale target) {
        if (srcLocale == null || target == null) return 0;
        return countTranslationsBySourceLocale(srcLocale.getLocaleId(), qualifiedName).stream()
                .filter(row -> target.equals(row[0]))
                .findFirst()
                .map(row -> (Long) row[1])
                .orElse(0L);
    }
}
