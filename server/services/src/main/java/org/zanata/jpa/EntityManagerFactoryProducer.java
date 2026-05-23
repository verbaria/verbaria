/*
 * Copyright 2014, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 */
package org.zanata.jpa;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.Typed;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnit;

import org.hibernate.SessionFactory;
import org.zanata.util.Zanata;

/**
 * Producer for {@link EntityManagerFactory} and {@link SessionFactory}.
 *
 * In Hibernate 5 the producer returned {@code HibernateEntityManagerFactory},
 * a Hibernate-specific extension of {@code EntityManagerFactory}. That type
 * was removed in Hibernate 6 — the standard {@code EntityManagerFactory}
 * itself unwraps to {@link SessionFactory} directly.
 */
@ApplicationScoped
public class EntityManagerFactoryProducer {

    @PersistenceUnit
    private EntityManagerFactory entityManagerFactory;

    @Produces
    @ApplicationScoped
    @Default
    @Zanata
    protected EntityManagerFactory create() {
        return entityManagerFactory;
    }

    @Produces
    @ApplicationScoped
    @Default
    @Zanata
    // @Typed restricts resolvable types to SessionFactory only. In
    // Hibernate 6, SessionFactory extends EntityManagerFactory, so without
    // this restriction Weld 5 treats this producer as a second @Zanata
    // EntityManagerFactory and conflicts with create() above.
    @Typed(SessionFactory.class)
    protected SessionFactory getSession(@Zanata EntityManagerFactory factory) {
        return factory.unwrap(SessionFactory.class);
    }
}
