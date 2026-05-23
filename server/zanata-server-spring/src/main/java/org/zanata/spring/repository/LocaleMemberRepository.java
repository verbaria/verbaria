package org.zanata.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.zanata.model.HLocale;
import org.zanata.model.HLocaleMember;
import org.zanata.model.HPerson;

@Repository
public interface LocaleMemberRepository
        extends JpaRepository<HLocaleMember, HLocaleMember.HLocaleMemberPk> {

    @Query("""
            select m from HLocaleMember m
            where m.id.supportedLanguage = :locale
              and m.id.person = :person
            """)
    Optional<HLocaleMember> findByLocaleAndPerson(@Param("locale") HLocale locale,
                                                  @Param("person") HPerson person);

    @Query("""
            select count(m) from HLocaleMember m
            where m.id.supportedLanguage.id = :localeId
            """)
    long countByLocaleId(@Param("localeId") Long localeId);
}
