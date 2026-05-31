package org.verbaria.server.headless.devseed;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HPerson;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.RoleRepository;
import org.verbaria.server.headless.security.Roles;

@Component
@Profile("production")
@Order(100)
public class AdminAccountBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountBootstrap.class);

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${VERBARIA_ADMIN_LOGIN:}")
    private String adminLogin;

    @Value("${VERBARIA_ADMIN_PASSWORD:}")
    private String adminPassword;

    public AdminAccountBootstrap(AccountRepository accountRepository,
                                 RoleRepository roleRepository,
                                 PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (adminLogin == null || adminLogin.isBlank()
                || adminPassword == null || adminPassword.isBlank()) {
            return;
        }
        if (accountRepository.findByUsername(adminLogin).isPresent()) {
            log.info("Admin account '{}' already exists; leaving it untouched.", adminLogin);
            return;
        }
        HAccountRole adminRole = roleRepository.findByName(Roles.ADMIN).orElseThrow();
        HAccountRole userRole = roleRepository.findByName(Roles.USER).orElseThrow();

        HAccount account = new HAccount();
        account.setUsername(adminLogin);
        account.setPasswordHash(passwordEncoder.encode(adminPassword));
        account.setEnabled(true);
        account.setRoles(new HashSet<>(Set.of(adminRole, userRole)));

        HPerson person = new HPerson();
        person.setName(adminLogin);
        person.setEmail(adminLogin + "@verbaria.local");
        person.setAccount(account);
        account.setPerson(person);

        accountRepository.save(account);
        log.info("Bootstrapped admin account '{}' from VERBARIA_ADMIN_LOGIN/PASSWORD.", adminLogin);
    }
}
