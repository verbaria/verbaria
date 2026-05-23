package org.zanata.spring.repository;

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
}
