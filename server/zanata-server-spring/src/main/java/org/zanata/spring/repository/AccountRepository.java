package org.zanata.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccount;

@Repository
public interface AccountRepository extends JpaRepository<HAccount, Long> {
    Optional<HAccount> findByUsername(String username);
}
