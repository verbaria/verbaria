package org.zanata.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HApplicationConfiguration;

@Repository
public interface ApplicationConfigurationRepository
        extends JpaRepository<HApplicationConfiguration, Long> {
    Optional<HApplicationConfiguration> findByKey(String key);
}
