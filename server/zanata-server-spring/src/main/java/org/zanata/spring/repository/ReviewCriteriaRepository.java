package org.zanata.spring.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.zanata.model.ReviewCriteria;

@Repository
public interface ReviewCriteriaRepository
        extends JpaRepository<ReviewCriteria, Long> {
}
