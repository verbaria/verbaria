package org.zanata.spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.LanguageRequest;
import org.zanata.model.type.RequestState;

@Repository
public interface LanguageRequestRepository extends JpaRepository<LanguageRequest, Long> {

    @Query("""
            select lr from LanguageRequest lr
            join fetch lr.request r
            join fetch r.requester
            left join fetch r.requester.person
            where lr.locale = :locale
              and r.state = :state
            order by r.validFrom asc
            """)
    List<LanguageRequest> findByLocaleAndState(@Param("locale") HLocale locale,
                                               @Param("state") RequestState state);

    @Query("""
            select lr from LanguageRequest lr
            join fetch lr.request r
            where lr.locale = :locale
              and r.requester = :requester
              and r.state = :state
            """)
    Optional<LanguageRequest> findOpenByLocaleAndRequester(@Param("locale") HLocale locale,
                                                           @Param("requester") HAccount requester,
                                                           @Param("state") RequestState state);
}
