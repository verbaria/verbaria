package org.verbaria.server.headless.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HGlossaryTerm;

@Repository
public interface GlossaryTermRepository extends JpaRepository<HGlossaryTerm, Long> {
}
