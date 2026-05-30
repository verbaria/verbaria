package org.zanata.spring.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccount;

@Repository
public interface AccountRepository extends JpaRepository<HAccount, Long> {
    Optional<HAccount> findByUsername(String username);

    /** Look up an account by its person's email (case-insensitive). */
    Optional<HAccount> findFirstByPersonEmailIgnoreCase(String email);

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

    /** Case-insensitive substring filter for the admin User Manager grid. */
    @Query("""
            select a from HAccount a
            where lower(a.username) like concat('%', lower(:q), '%')
            """)
    org.springframework.data.domain.Page<HAccount> findByUsernameContaining(
            @Param("q") String q,
            org.springframework.data.domain.Pageable pageable);

    @Query("""
            select count(a) from HAccount a
            where lower(a.username) like concat('%', lower(:q), '%')
            """)
    long countByUsernameContaining(@Param("q") String q);

    /**
     * Initialises {@code roles} on an already-loaded page of accounts in a
     * single query. Used to keep DB-side pagination (a collection join-fetch
     * combined with a page limit forces inefficient in-memory pagination) while
     * still making roles available to callers that render outside an open
     * session — e.g. the admin User Manager grid.
     */
    @Query("""
            select distinct a from HAccount a
            left join fetch a.roles
            where a in :accounts
            """)
    List<HAccount> fetchRolesFor(@Param("accounts") Collection<HAccount> accounts);
}
