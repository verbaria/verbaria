package org.zanata.spring.devseed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.zanata.model.HAccountRole;
import org.zanata.spring.repository.RoleRepository;
import org.zanata.spring.security.Roles;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoleBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleBootstrap.class);

    private final RoleRepository roleRepository;

    public RoleBootstrap(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        ensureRole(Roles.ADMIN);
        ensureRole(Roles.USER);
    }

    private void ensureRole(String name) {
        if (roleRepository.findByName(name).isPresent()) {
            return;
        }
        HAccountRole role = new HAccountRole();
        role.setName(name);
        roleRepository.save(role);
        log.info("Created role '{}'.", name);
    }
}
