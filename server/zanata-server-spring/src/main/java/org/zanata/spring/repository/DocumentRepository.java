package org.zanata.spring.repository;

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
}
