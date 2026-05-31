package org.verbaria.server.headless.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HIterationGroup;

@Repository
public interface IterationGroupRepository extends JpaRepository<HIterationGroup, Long> {

    @Query("""
            select g from HIterationGroup g
            where :q = ''
               or lower(g.name) like concat('%', lower(:q), '%')
               or lower(g.slug) like concat('%', lower(:q), '%')
               or lower(g.description) like concat('%', lower(:q), '%')
            """)
    Page<HIterationGroup> search(@Param("q") String q, Pageable pageable);

    Optional<HIterationGroup> findBySlug(String slug);

    @Query("""
            select distinct g from HIterationGroup g
            left join fetch g.projectIterations pi
            left join fetch pi.project
            left join fetch g.maintainers
            left join fetch g.activeLocales
            where g.slug = :slug
            """)
    Optional<HIterationGroup> findBySlugWithFetch(@Param("slug") String slug);
}
