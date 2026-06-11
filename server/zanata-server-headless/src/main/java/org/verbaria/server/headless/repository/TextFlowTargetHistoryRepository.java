package org.verbaria.server.headless.repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.LocaleId;
import org.zanata.model.HTextFlowTargetHistory;

@Repository
public interface TextFlowTargetHistoryRepository
        extends JpaRepository<HTextFlowTargetHistory, Long> {

    /**
     * History rows for one text-flow / locale with {@code lastModifiedBy}
     * eagerly fetched so the History panel can render the modifier's name
     * outside the original transaction.
     */
    @Query("""
            select h from HTextFlowTargetHistory h
            left join fetch h.lastModifiedBy
            where h.textFlowTarget.textFlow.id = :textFlowId
              and h.textFlowTarget.locale.localeId = :locale
            order by h.versionNum desc
            """)
    List<HTextFlowTargetHistory> findByTextFlowAndLocale(
            @Param("textFlowId") Long textFlowId,
            @Param("locale") LocaleId locale);

    /**
     * Prior states across all targets (each row is a past action), with actor,
     * text-flow, document, version and project eagerly fetched for the activity
     * feed. Optional user/project filters: a null value means "no filter".
     */
    @Query("""
            select h from HTextFlowTargetHistory h
              join fetch h.lastModifiedBy per
              join fetch per.account acc
              join fetch h.textFlowTarget t
              join fetch t.textFlow tf
              join fetch tf.document doc
              join fetch doc.projectIteration it
              join fetch it.project p
            where h.lastModifiedBy is not null
              and h.state is not null
              and h.state <> org.zanata.common.ContentState.New
              and (:username is null or acc.username = :username)
              and (:projectSlug is null or p.slug = :projectSlug)
              and (:locale is null or t.locale.localeId = :locale)
              and h.lastChanged >= :from
              and h.lastChanged < :to
            order by h.lastChanged desc
            """)
    List<HTextFlowTargetHistory> findRecentActivity(
            @Param("username") String username,
            @Param("projectSlug") String projectSlug,
            @Param("locale") LocaleId locale,
            @Param("from") Date from,
            @Param("to") Date to, Pageable pageable);

    /**
     * All historical versions for the given text flows (all locales), used by
     * the activity feed to reconstruct the previous value of each change —
     * unfiltered, so the prior value shows regardless of who set it.
     */
    @Query("""
            select h from HTextFlowTargetHistory h
              join fetch h.textFlowTarget t
              join fetch t.locale
            where t.textFlow.id in :textFlowIds
            """)
    List<HTextFlowTargetHistory> findForTextFlows(
            @Param("textFlowIds") Collection<Long> textFlowIds);
}
