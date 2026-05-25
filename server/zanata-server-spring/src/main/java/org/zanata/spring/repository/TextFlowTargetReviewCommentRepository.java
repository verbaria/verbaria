package org.zanata.spring.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HTextFlowTargetReviewComment;

@Repository
public interface TextFlowTargetReviewCommentRepository
        extends JpaRepository<HTextFlowTargetReviewComment, Long> {

    @Query("""
            select c from HTextFlowTargetReviewComment c
            where c.textFlowTarget.locale.localeId = :locale
            order by c.creationDate desc
            """)
    List<HTextFlowTargetReviewComment> findByLocale(@Param("locale") LocaleId locale);

    @Query("""
            select c from HTextFlowTargetReviewComment c
            where c.textFlowTarget.textFlow.id = :textFlowId
              and c.textFlowTarget.locale.localeId = :locale
            order by c.creationDate desc
            """)
    List<HTextFlowTargetReviewComment> findByTextFlowAndLocale(
            @Param("textFlowId") Long textFlowId,
            @Param("locale") LocaleId locale);
}
