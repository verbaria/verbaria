package org.zanata.spring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.HGlossaryTerm;

@Repository
public interface GlossaryTermRepository extends JpaRepository<HGlossaryTerm, Long> {
}
