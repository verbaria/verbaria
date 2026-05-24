package org.zanata.spring.repository;

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
}
