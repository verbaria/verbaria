package org.verbaria.server.headless.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HDocument;

@Repository
public interface DocumentRepository extends JpaRepository<HDocument, Long> {

    @Query("""
            select d from HDocument d
            where d.projectIteration.slug = :versionSlug
              and d.projectIteration.project.slug = :projectSlug
              and d.obsolete = false
            order by d.path, d.name
            """)
    List<HDocument> findByVersion(@Param("projectSlug") String projectSlug,
                                  @Param("versionSlug") String versionSlug);

    @Query("""
            select d from HDocument d
            where d.projectIteration.slug = :versionSlug
              and d.projectIteration.project.slug = :projectSlug
              and d.docId = :docId
              and d.obsolete = false
            """)
    Optional<HDocument> findByVersionAndDocId(@Param("projectSlug") String projectSlug,
                                              @Param("versionSlug") String versionSlug,
                                              @Param("docId") String docId);

    @Query("""
            select d from HDocument d
            join fetch d.projectIteration pi
            join fetch pi.project
            where d.docId = :docId
              and d.obsolete = false
            """)
    List<HDocument> findByDocIdAcrossProjects(@Param("docId") String docId);

    /** Total non-obsolete document count for a project/version — cheap. */
    @Query("""
            select count(d) from HDocument d
            where d.projectIteration.slug = :versionSlug
              and d.projectIteration.project.slug = :projectSlug
              and d.obsolete = false
            """)
    long countByVersion(@Param("projectSlug") String projectSlug,
                        @Param("versionSlug") String versionSlug);

    /**
     * Server-paged + search-filtered docs for the version's Documents grid.
     * {@code q} is a case-insensitive substring match against docId; pass an
     * empty string to disable filtering.
     */
    @Query("""
            select d from HDocument d
            where d.projectIteration.slug = :versionSlug
              and d.projectIteration.project.slug = :projectSlug
              and d.obsolete = false
              and ( :q = ''
                    or lower(d.docId) like concat('%', :q, '%')
                    or (d.path is not null and lower(d.path) like concat('%', :q, '%')) )
            order by d.path, d.name
            """)
    List<HDocument> pageByVersion(@Param("projectSlug") String projectSlug,
                                  @Param("versionSlug") String versionSlug,
                                  @Param("q") String lowerQ,
                                  org.springframework.data.domain.Pageable page);

    @Query("""
            select count(d) from HDocument d
            where d.projectIteration.slug = :versionSlug
              and d.projectIteration.project.slug = :projectSlug
              and d.obsolete = false
              and ( :q = ''
                    or lower(d.docId) like concat('%', :q, '%')
                    or (d.path is not null and lower(d.path) like concat('%', :q, '%')) )
            """)
    long countMatchingByVersion(@Param("projectSlug") String projectSlug,
                                @Param("versionSlug") String versionSlug,
                                @Param("q") String lowerQ);
}
