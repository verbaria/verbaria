package org.zanata.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccount;

@Repository
public interface AccountRepository extends JpaRepository<HAccount, Long> {
    Optional<HAccount> findByUsername(String username);

    /**
     * Eager-loads {@code roles} so callers running outside an open Hibernate
     * session — notably {@link org.zanata.spring.security.ApiKeyAuthenticationFilter}
     * which executes before any controller transaction boundary — can iterate
     * authorities without tripping {@code LazyInitializationException}.
     */
    @Query("""
            select distinct a from HAccount a
            left join fetch a.roles
            where a.username = :username
            """)
    Optional<HAccount> findByUsernameWithRoles(@Param("username") String username);
}
