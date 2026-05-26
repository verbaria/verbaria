package org.zanata.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Zanata server.
 *
 * The JPA entities live in zanata-model (as a JAR dependency) so
 * {@link EntityScan} reaches outside the app package; Spring Data
 * repositories live in this module under org.zanata.spring.repository.
 */
@SpringBootApplication
@EntityScan(basePackages = "org.zanata.model")
@EnableJpaRepositories(basePackages = "org.zanata.spring.repository")
@EnableScheduling
public class ZanataServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZanataServerApplication.class, args);
    }
}
