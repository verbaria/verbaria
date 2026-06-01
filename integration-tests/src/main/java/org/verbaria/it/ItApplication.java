package org.verbaria.it;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"org.verbaria.server.headless", "org.verbaria.it"})
@EntityScan("org.zanata.model")
@EnableJpaRepositories("org.verbaria.server.headless.repository")
public class ItApplication {
}
