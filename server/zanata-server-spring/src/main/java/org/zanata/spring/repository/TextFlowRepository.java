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
}
