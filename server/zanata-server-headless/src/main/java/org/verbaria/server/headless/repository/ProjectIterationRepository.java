package org.verbaria.server.headless.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HProjectIteration;

@Repository
public interface ProjectIterationRepository extends JpaRepository<HProjectIteration, Long> {

    @Query("""
            select i from HProjectIteration i
            where i.slug = :versionSlug
              and i.project.slug = :projectSlug
            """)
    Optional<HProjectIteration> findByProjectAndSlug(@Param("projectSlug") String projectSlug,
                                                     @Param("versionSlug") String versionSlug);

    @Query("""
            select i from HProjectIteration i
            left join fetch i.project p
            left join fetch p.defaultSourceLocale
            where i.slug = :versionSlug
              and i.project.slug = :projectSlug
            """)
    Optional<HProjectIteration> findFullByProjectAndSlug(@Param("projectSlug") String projectSlug,
                                                         @Param("versionSlug") String versionSlug);

    /**
     * Returns the project's {@code customizedLocales} set when
     * {@code overrideLocales=true}; otherwise empty (caller falls back to
     * the server-wide active locale list).
     */
    @Query("""
            select cl from HProjectIteration i
            join i.project p
            join p.customizedLocales cl
            where i.id = :iterId
              and p.overrideLocales = true
            """)
    java.util.List<org.zanata.model.HLocale> findCustomizedLocalesIfOverride(
            @Param("iterId") Long iterId);

    default Optional<java.util.Collection<org.zanata.model.HLocale>> findProjectLocales(Long iterId) {
        java.util.List<org.zanata.model.HLocale> list = findCustomizedLocalesIfOverride(iterId);
        return list == null || list.isEmpty()
                ? Optional.empty() : Optional.of(list);
    }

    @Query("""
            select p.defaultSourceLocale from HProjectIteration i
            join i.project p
            where i.id = :iterId
              and p.defaultSourceLocale is not null
            """)
    Optional<org.zanata.model.HLocale> findProjectSourceLocale(@Param("iterId") Long iterId);

    /**
     * Like {@link #findFullByProjectAndSlug} but also eagerly loads
     * {@code customizedLocales} so the Version Settings view can render the
     * locale selector without a {@code LazyInitializationException}.
     */
    @Query("""
            select distinct i from HProjectIteration i
            left join fetch i.project p
            left join fetch p.defaultSourceLocale
            left join fetch i.customizedLocales
            where i.slug = :versionSlug
              and i.project.slug = :projectSlug
            """)
    Optional<HProjectIteration> findForSettings(@Param("projectSlug") String projectSlug,
                                                @Param("versionSlug") String versionSlug);

    @Query("""
            select distinct i from HProjectIteration i
            left join fetch i.customizedLocales
            where i.id = :id
            """)
    Optional<HProjectIteration> findForSettingsById(@Param("id") Long id);

    /** All non-obsolete versions of a project, with customized locales fetched. */
    @Query("""
            select distinct i from HProjectIteration i
            left join fetch i.customizedLocales
            where i.project.slug = :projectSlug
              and i.status <> org.zanata.common.EntityStatus.OBSOLETE
            order by i.slug
            """)
    java.util.List<HProjectIteration> findForSettingsByProject(
            @Param("projectSlug") String projectSlug);

    @Query("""
            select count(tf) from HTextFlow tf
            where tf.document.projectIteration.id = :id
              and tf.obsolete = false
            """)
    long countSourceTextFlows(@Param("id") Long iterId);

    @Query("""
            select coalesce(sum(tf.wordCount), 0) from HTextFlow tf
            where tf.document.projectIteration.id = :id
              and tf.obsolete = false
            """)
    long sumSourceWordCount(@Param("id") Long iterId);

    /** Non-obsolete iteration ids of a project, by slug. */
    @Query("""
            select i.id from HProjectIteration i
            where i.project.slug = :slug
              and i.status <> org.zanata.common.EntityStatus.OBSOLETE
            """)
    java.util.List<Long> findIterationIdsByProjectSlug(@Param("slug") String slug);
}
