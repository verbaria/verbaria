package org.zanata.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Entry point for the Spring Boot rewrite of the Zanata server.
 *
 * Migration is happening UI-first: each React screen drives which REST
 * endpoint, JPA repository and service classes get moved over from the
 * WildFly modules.  The JPA entities still live in zanata-model (as a
 * JAR dependency) so {@link EntityScan} reaches outside the app
 * package; Spring Data repositories live in this module under
 * org.zanata.spring.repository.
 */
@SpringBootApplication
@EntityScan(basePackages = "org.zanata.model")
@EnableJpaRepositories(basePackages = "org.zanata.spring.repository")
public class ZanataServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZanataServerApplication.class, args);
    }
}
