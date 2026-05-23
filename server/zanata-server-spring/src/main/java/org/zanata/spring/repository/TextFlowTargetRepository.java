package org.zanata.spring.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HTextFlowTarget;

@Repository
public interface TextFlowTargetRepository extends JpaRepository<HTextFlowTarget, Long> {

    @Query("""
            select t from HTextFlowTarget t
            where t.textFlow.id = :textFlowId
              and t.locale.localeId = :locale
            """)
    Optional<HTextFlowTarget> findByTextFlowAndLocale(@Param("textFlowId") Long textFlowId,
                                                      @Param("locale") LocaleId locale);

    @Query("""
            select t from HTextFlowTarget t
            where t.textFlow.id in :textFlowIds
              and t.locale.localeId = :locale
            """)
    List<HTextFlowTarget> findByTextFlowIdsAndLocale(@Param("textFlowIds") Collection<Long> textFlowIds,
                                                     @Param("locale") LocaleId locale);

    @Query("""
            select t.state, count(t) from HTextFlowTarget t
            where t.textFlow.document.id = :docId
              and t.locale.localeId = :locale
            group by t.state
            """)
    List<Object[]> countByDocAndLocale(@Param("docId") Long docId,
                                       @Param("locale") LocaleId locale);
}
