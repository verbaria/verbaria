package org.zanata.spring.repository;

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
            left join fetch i.project
            where i.slug = :versionSlug
              and i.project.slug = :projectSlug
            """)
    Optional<HProjectIteration> findFullByProjectAndSlug(@Param("projectSlug") String projectSlug,
                                                         @Param("versionSlug") String versionSlug);

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
}
