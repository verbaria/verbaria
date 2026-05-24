package org.zanata.spring.repository;

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
            left join fetch tf.potEntryData
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
            left join fetch tf.potEntryData ped
            where tf.document.id = :docId
              and tf.obsolete = false
              and ( :q = ''
                    or lower(tf.resId) like concat('%', :q, '%')
                    or (ped is not null and lower(ped.context) like concat('%', :q, '%'))
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
            left join tf.potEntryData ped
            where tf.document.id = :docId
              and tf.obsolete = false
              and ( :q = ''
                    or lower(tf.resId) like concat('%', :q, '%')
                    or (ped is not null and lower(ped.context) like concat('%', :q, '%'))
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
}
