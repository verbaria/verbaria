package org.verbaria.server.headless.repository;

import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.Activity;

@Repository
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @Query("""
            select a from Activity a
            where a.actor.account.username = :username
            order by a.approxTime desc
            """)
    List<Activity> findByActor(@Param("username") String username, Pageable pageable);

    @Query("""
            select a from Activity a
            where a.actor.account.username = :username
              and a.approxTime >= :from
              and a.approxTime <  :to
            order by a.approxTime asc
            """)
    List<Activity> findByActorAndRange(@Param("username") String username,
                                       @Param("from") Date from,
                                       @Param("to") Date to);
}
