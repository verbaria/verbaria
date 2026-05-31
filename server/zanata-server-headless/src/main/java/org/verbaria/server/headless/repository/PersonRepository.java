package org.verbaria.server.headless.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HPerson;

@Repository
public interface PersonRepository extends JpaRepository<HPerson, Long> {

    @Query("""
            select p from HPerson p
            where :q = ''
               or lower(p.name) like concat('%', lower(:q), '%')
               or lower(p.email) like concat('%', lower(:q), '%')
               or lower(p.account.username) like concat('%', lower(:q), '%')
            """)
    Page<HPerson> search(@Param("q") String q, Pageable pageable);
}
