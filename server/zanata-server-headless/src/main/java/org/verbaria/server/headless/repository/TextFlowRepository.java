package org.verbaria.server.headless.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HTextFlow;

@Repository
public interface TextFlowRepository extends JpaRepository<HTextFlow, Long> {

    @Query("""
            select distinct tf from HTextFlow tf
            left join fetch tf.extensions
            where tf.document.id = :docId
              and tf.obsolete = false
            order by tf.pos
            """)
    List<HTextFlow> findByDocument(@Param("docId") Long docId);

    @Query("""
            select tf from HTextFlow tf
            where tf.id in :ids
              and tf.obsolete = false
            """)
    List<HTextFlow> findByIds(@Param("ids") Collection<Long> ids);

    /** Every text flow of a document, obsolete ones included (for hard delete). */
    @Query("""
            select tf from HTextFlow tf
            where tf.document.id = :docId
            """)
    List<HTextFlow> findAllByDocumentIncludingObsolete(@Param("docId") Long docId);

    /**
     * Eagerly initialises the lazy {@code extensions} collection for the given
     * text flows. Call within the same session/request as the page query so the
     * entities carry their extensions once the grid renders them detached
     * (a collection can't be join-fetched together with pagination).
     */
    @Query("""
            select distinct tf from HTextFlow tf
            left join fetch tf.extensions
            where tf.id in :ids
            """)
    List<HTextFlow> findWithExtensions(@Param("ids") Collection<Long> ids);

    @Query("""
            select tf.document.id, count(tf)
            from HTextFlow tf
            where tf.document.projectIteration.id = :iterId
              and tf.obsolete = false
            group by tf.document.id
            """)
    List<Object[]> countPerDocForIteration(@Param("iterId") Long iterId);

    /** Translated/Approved targets whose source moved on since — needs review. */
    @Query("""
            select count(t) from HTextFlowTarget t
            where t.textFlow.document.projectIteration.id = :iterId
              and t.locale.localeId = :locale
              and t.textFlow.obsolete = false
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
              and t.textFlowRevision < t.textFlow.revision
            """)
    long countNeedsReviewInIteration(@Param("iterId") Long iterId,
            @Param("locale") org.zanata.common.LocaleId locale);

    @Query("""
            select t.textFlow.document.id, count(t) from HTextFlowTarget t
            where t.textFlow.document.projectIteration.id = :iterId
              and t.locale.localeId = :locale
              and t.textFlow.obsolete = false
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
              and t.textFlowRevision < t.textFlow.revision
            group by t.textFlow.document.id
            """)
    List<Object[]> countNeedsReviewPerDoc(@Param("iterId") Long iterId,
            @Param("locale") org.zanata.common.LocaleId locale);

    @Query("""
            select count(t) from HTextFlowTarget t
            where t.textFlow.document.id = :docId
              and t.locale.localeId = :locale
              and t.textFlow.obsolete = false
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
              and t.textFlowRevision < t.textFlow.revision
            """)
    long countNeedsReviewForDoc(@Param("docId") Long docId,
            @Param("locale") org.zanata.common.LocaleId locale);

    @Query("""
            select t.locale.localeId, count(t) from HTextFlowTarget t
            where t.textFlow.document.projectIteration.id = :iterId
              and t.textFlow.obsolete = false
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
              and t.textFlowRevision < t.textFlow.revision
            group by t.locale.localeId
            """)
    List<Object[]> countNeedsReviewPerLocale(@Param("iterId") Long iterId);

    /**
     * Page of textflows for the translate view, filtered server-side by a
     * search term and a tri-state translation-completeness predicate.
     *
     * @param stateMode 0=any, 1=incomplete only, 2=complete only,
     *                  3=needs review (translated but the source changed since),
     *                  4=need approve (translated but not yet approved)
     */
    @Query("""
            select tf from HTextFlow tf
            where tf.document.id = :docId
              and tf.obsolete = false
              and ( :q = ''
                    or lower(tf.resId) like concat('%', :q, '%')
                    or exists (
                          select 1 from HTextFlowExtension e
                          where e.textFlow = tf
                            and lower(e.searchText) like concat('%', :q, '%'))
                    or lower(coalesce(tf.content0, '')) like concat('%', :q, '%') )
              and (
                    :stateMode = 0
                    or ( :stateMode = 1
                         and not exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)) )
                    or ( :stateMode = 2
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)) )
                    or ( :stateMode = 3
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)
                                and t.textFlowRevision < tf.revision) )
                    or ( :stateMode = 4
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and t.state = org.zanata.common.ContentState.Translated) )
              )
            order by tf.pos
            """)
    List<HTextFlow> pageForTranslateView(@Param("docId") Long docId,
                                         @Param("locale") org.zanata.common.LocaleId locale,
                                         @Param("q") String lowerQuery,
                                         @Param("stateMode") int stateMode,
                                         org.springframework.data.domain.Pageable page);

    @Query("""
            select count(tf) from HTextFlow tf
            where tf.document.id = :docId
              and tf.obsolete = false
              and ( :q = ''
                    or lower(tf.resId) like concat('%', :q, '%')
                    or exists (
                          select 1 from HTextFlowExtension e
                          where e.textFlow = tf
                            and lower(e.searchText) like concat('%', :q, '%'))
                    or lower(coalesce(tf.content0, '')) like concat('%', :q, '%') )
              and (
                    :stateMode = 0
                    or ( :stateMode = 1
                         and not exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)) )
                    or ( :stateMode = 2
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)) )
                    or ( :stateMode = 3
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and (t.state = org.zanata.common.ContentState.Translated
                                     or t.state = org.zanata.common.ContentState.Approved)
                                and t.textFlowRevision < tf.revision) )
                    or ( :stateMode = 4
                         and exists (
                              select 1 from HTextFlowTarget t
                              where t.textFlow = tf
                                and t.locale.localeId = :locale
                                and t.state = org.zanata.common.ContentState.Translated) )
              )
            """)
    long countForTranslateView(@Param("docId") Long docId,
                               @Param("locale") org.zanata.common.LocaleId locale,
                               @Param("q") String lowerQuery,
                               @Param("stateMode") int stateMode);

    /**
     * All text flows in the iteration that don't yet have a Translated or
     * Approved target in {@code locale}. Used by Copy Trans to know what to
     * fill in.
     */
    @Query("""
            select tf from HTextFlow tf
            where tf.document.projectIteration.id = :iterId
              and tf.obsolete = false
              and not exists (
                  select 1 from HTextFlowTarget t
                  where t.textFlow = tf
                    and t.locale.localeId = :locale
                    and (t.state = org.zanata.common.ContentState.Translated
                         or t.state = org.zanata.common.ContentState.Approved))
            order by tf.id
            """)
    List<HTextFlow> findUntranslatedInIteration(
            @Param("iterId") Long iterId,
            @Param("locale") org.zanata.common.LocaleId locale);

    /**
     * Candidate translation targets for Copy Trans: other text flows whose
     * source content matches {@code contentHash} and that already have a
     * Translated or Approved target in {@code locale}. Eagerly fetches the
     * source-side context the rule engine needs.
     */
    @Query("""
            select t from HTextFlowTarget t
            join fetch t.textFlow ctf
            join fetch ctf.document cdoc
            join fetch cdoc.projectIteration citer
            join fetch citer.project cproj
            left join fetch ctf.extensions
            where t.locale.localeId = :locale
              and (t.state = org.zanata.common.ContentState.Translated
                   or t.state = org.zanata.common.ContentState.Approved)
              and ctf.contentHash = :hash
              and ctf.id <> :excludeId
              and ctf.obsolete = false
            order by t.lastChanged desc
            """)
    List<org.zanata.model.HTextFlowTarget> findCopyTransCandidates(
            @Param("hash") String contentHash,
            @Param("excludeId") Long excludeTextFlowId,
            @Param("locale") org.zanata.common.LocaleId locale);
}
