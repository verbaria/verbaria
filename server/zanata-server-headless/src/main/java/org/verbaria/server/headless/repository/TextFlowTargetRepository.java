package org.verbaria.server.headless.repository;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
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

    /**
     * Same as {@link #findByTextFlowAndLocale} but eagerly fetches
     * {@code lastModifiedBy} so the History panel can read the modifier's
     * name outside the original transaction.
     */
    @Query("""
            select t from HTextFlowTarget t
            left join fetch t.lastModifiedBy
            where t.textFlow.id = :textFlowId
              and t.locale.localeId = :locale
            """)
    Optional<HTextFlowTarget> findByTextFlowAndLocaleWithModifier(
            @Param("textFlowId") Long textFlowId,
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

    @Query("""
            select t.locale, t.state, sum(t.textFlow.wordCount)
            from HTextFlowTarget t
            where t.textFlow.document.projectIteration.id = :id
              and t.textFlow.obsolete = false
            group by t.locale, t.state
            """)
    List<Object[]> aggregateWordsByLocaleAndState(@Param("id") Long iterId);

    @Query("""
            select t.textFlow.document.id, count(t)
            from HTextFlowTarget t
            where t.textFlow.document.projectIteration.id = :id
              and t.locale.localeId = :locale
              and t.textFlow.obsolete = false
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
            group by t.textFlow.document.id
            """)
    List<Object[]> translatedCountPerDocForLocale(@Param("id") Long iterId,
                                                  @Param("locale") LocaleId locale);

    /**
     * Latest state per target (the most recent action), with the actor,
     * text-flow, document, version and project eagerly fetched so the activity
     * feed can render detached rows. Optional user/project filters: a null
     * value means "no filter".
     */
    @Query("""
            select t from HTextFlowTarget t
              join fetch t.lastModifiedBy per
              join fetch per.account acc
              join fetch t.textFlow tf
              join fetch tf.document doc
              join fetch doc.projectIteration it
              join fetch it.project p
            where t.lastModifiedBy is not null
              and t.state is not null
              and t.state <> org.zanata.common.ContentState.New
              and (:username is null or acc.username = :username)
              and (:projectSlug is null or p.slug = :projectSlug)
              and (:locale is null or t.locale.localeId = :locale)
              and t.lastChanged >= :from
              and t.lastChanged < :to
            order by t.lastChanged desc
            """)
    List<HTextFlowTarget> findRecentActivity(@Param("username") String username,
            @Param("projectSlug") String projectSlug,
            @Param("locale") LocaleId locale,
            @Param("from") Date from,
            @Param("to") Date to, Pageable pageable);

    /**
     * Current targets for the given text flows (all locales), used by the
     * activity feed to reconstruct the previous value of each change —
     * unfiltered, so the prior value shows regardless of who set it.
     */
    @Query("""
            select t from HTextFlowTarget t
              join fetch t.locale
            where t.textFlow.id in :textFlowIds
            """)
    List<HTextFlowTarget> findForTextFlows(
            @Param("textFlowIds") Collection<Long> textFlowIds);
}
