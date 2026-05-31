package org.verbaria.server.headless.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccountRole;

@Repository
public interface RoleRepository extends JpaRepository<HAccountRole, Integer> {
    Optional<HAccountRole> findByName(String name);
}
