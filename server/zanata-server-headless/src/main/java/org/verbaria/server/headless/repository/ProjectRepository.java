package org.verbaria.server.headless.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HProject;

/**
 * Spring Data repository for the bits of HProject the /explore screen needs:
 * non-obsolete non-read-only public projects, optionally filtered by
 * name/slug/description via a case-insensitive LIKE.
 */
@Repository
public interface ProjectRepository extends JpaRepository<HProject, Long> {

    @Query("""
            select p from HProject p
            where p.status <> org.zanata.common.EntityStatus.OBSOLETE
              and p.status <> org.zanata.common.EntityStatus.READONLY
              and (:q = ''
                   or lower(p.name) like concat('%', lower(:q), '%')
                   or lower(p.slug) like concat('%', lower(:q), '%')
                   or lower(p.description) like concat('%', lower(:q), '%'))
            """)
    Page<HProject> search(@Param("q") String q, Pageable pageable);

    Optional<HProject> findBySlug(String slug);

    @Query("""
            select distinct p from HProject p
            left join fetch p.projectIterations
            left join fetch p.members
            where p.slug = :slug
            """)
    Optional<HProject> findBySlugWithIterations(@Param("slug") String slug);

    @Query("""
            select distinct p from HProject p
            left join fetch p.customizedLocales
            left join fetch p.defaultSourceLocale
            where p.slug = :slug
            """)
    Optional<HProject> findBySlugWithLocales(@Param("slug") String slug);

    /**
     * Eagerly fetch {@code members} + each member's {@code person.account} so
     * the project People tab can render usernames/emails without lazy hits.
     */
    @Query("""
            select distinct p from HProject p
            left join fetch p.members m
            left join fetch m.person per
            left join fetch per.account
            where p.slug = :slug
            """)
    Optional<HProject> findBySlugWithMembers(@Param("slug") String slug);

    /** Slugs of projects the given account maintains (for showing manage links). */
    @Query("""
            select distinct p.slug from HProject p
            join p.members m
            where m.role = org.zanata.model.ProjectRole.Maintainer
              and m.person.account.username = :username
            """)
    List<String> findMaintainedSlugs(@Param("username") String username);
}
