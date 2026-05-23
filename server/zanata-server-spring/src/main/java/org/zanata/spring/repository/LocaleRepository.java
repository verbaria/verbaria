package org.zanata.spring.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;

@Repository
public interface LocaleRepository extends JpaRepository<HLocale, Long> {

    @Query("""
            select l from HLocale l
            where :q = ''
               or lower(l.displayName) like concat('%', lower(:q), '%')
               or lower(l.nativeName)  like concat('%', lower(:q), '%')
            """)
    Page<HLocale> search(@Param("q") String q, Pageable pageable);

    Optional<HLocale> findByLocaleId(LocaleId localeId);
}
