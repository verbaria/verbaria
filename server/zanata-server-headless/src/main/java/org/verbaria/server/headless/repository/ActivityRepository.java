package org.verbaria.server.headless.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.common.ActivityType;
import org.zanata.model.Activity;
import org.zanata.model.type.EntityType;

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
            where a.actor.id = :actorId
              and a.activityType = :type
              and a.contextType = :contextType
              and a.contextId = :contextId
              and a.approxTime = :hour
            """)
    Optional<Activity> findInHour(@Param("actorId") Long actorId,
                                  @Param("type") ActivityType type,
                                  @Param("contextType") EntityType contextType,
                                  @Param("contextId") long contextId,
                                  @Param("hour") Date hour);

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
