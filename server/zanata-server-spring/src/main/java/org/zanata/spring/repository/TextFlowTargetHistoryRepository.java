package org.zanata.spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HTextFlowTargetHistory;

@Repository
public interface TextFlowTargetHistoryRepository
        extends JpaRepository<HTextFlowTargetHistory, Long> {

    @Query("""
            select h from HTextFlowTargetHistory h
            where h.textFlowTarget.textFlow.id = :textFlowId
              and h.textFlowTarget.locale.localeId = :locale
            order by h.versionNum desc
            """)
    List<HTextFlowTargetHistory> findByTextFlowAndLocale(
            @Param("textFlowId") Long textFlowId,
            @Param("locale") LocaleId locale);
}
