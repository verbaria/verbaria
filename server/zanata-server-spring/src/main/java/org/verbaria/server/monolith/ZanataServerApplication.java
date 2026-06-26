package org.verbaria.server.monolith;

import com.vaadin.flow.spring.annotation.EnableVaadin;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.verbaria.server", "org.zanata.adapter"})
@EntityScan(basePackages = "org.zanata.model")
@EnableJpaRepositories(basePackages = "org.verbaria.server.headless.repository")
@EnableVaadin({
    "org.verbaria.server.ui",
    "org.verbaria.server.monolith",
})
@EnableScheduling
public class ZanataServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZanataServerApplication.class, args);
    }
}
