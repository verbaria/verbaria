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

    @Query("""
            select tf.document.id, count(tf)
            from HTextFlow tf
            where tf.document.projectIteration.id = :iterId
              and tf.obsolete = false
            group by tf.document.id
            """)
    List<Object[]> countPerDocForIteration(@Param("iterId") Long iterId);

    /**
     * Page of textflows for the translate view, filtered server-side by a
     * search term and a tri-state translation-completeness predicate.
     *
     * @param stateMode 0=any, 1=incomplete only, 2=complete only
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
