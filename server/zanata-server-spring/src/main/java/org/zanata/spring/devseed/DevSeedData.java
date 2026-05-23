package org.zanata.spring.devseed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.repository.RoleRepository;

/**
 * Inserts a handful of demo projects on startup so the React /explore screen
 * has something to render against the H2 in-memory database. Active only
 * when the H2 datasource is in use.
 */
@Component
@Profile("!postgres")
public class DevSeedData implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedData.class);

    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final ProjectIterationRepository iterationRepository;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSeedData(ProjectRepository projectRepository,
                       LocaleRepository localeRepository,
                       ProjectIterationRepository iterationRepository,
                       AccountRepository accountRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.iterationRepository = iterationRepository;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRolesAndAccounts();
        seedProjects();
        seedLocales();
    }

    private void seedRolesAndAccounts() {
        if (accountRepository.count() > 0) return;
        HAccountRole admin = saveRole("admin");
        HAccountRole user = saveRole("user");
        saveAccount("admin", "admin", "Administrator", "admin@example.com", Set.of(admin, user));
        saveAccount("dev",   "dev",   "Dev User",      "dev@example.com",   Set.of(user));
        log.info("Seeded {} roles + {} accounts", roleRepository.count(), accountRepository.count());
    }

    private HAccountRole saveRole(String name) {
        HAccountRole r = new HAccountRole();
        r.setName(name);
        return roleRepository.save(r);
    }

    private void saveAccount(String username, String password, String fullName,
                             String email, Set<HAccountRole> roles) {
        HAccount a = new HAccount();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(password));
        a.setEnabled(true);
        a.setRoles(new HashSet<>(roles));
        HPerson p = new HPerson();
        p.setName(fullName);
        p.setEmail(email);
        p.setAccount(a);
        a.setPerson(p);
        accountRepository.save(a);
    }

    private void seedProjects() {
        if (projectRepository.count() > 0) return;
        saveProject("about-fedora", "About Fedora",
                "Translations for the About Fedora release notes module.");
        saveProject("rhel-installation-guide", "RHEL Installation Guide",
                "Red Hat Enterprise Linux installation documentation.");
        saveProject("gnome-shell", "GNOME Shell",
                "Translatable strings for the GNOME desktop shell.");
        saveProject("zanata-platform", "Zanata Platform",
                "Localization platform UI strings (meta!).");
        log.info("Inserted {} demo projects", projectRepository.count());
    }

    private void seedLocales() {
        if (localeRepository.count() > 0) return;
        saveLocale("en-US", "English (US)",     "English",  true);
        saveLocale("fr",    "French",           "Français", false);
        saveLocale("de",    "German",           "Deutsch",  false);
        saveLocale("ja",    "Japanese",         "日本語",     false);
        saveLocale("zh-CN", "Simplified Chinese","简体中文",  false);
        saveLocale("ru",    "Russian",          "Русский",  false);
        log.info("Inserted {} demo locales", localeRepository.count());
    }

    private void saveProject(String slug, String name, String desc) {
        HProject p = new HProject();
        p.setSlug(slug);
        p.setName(name);
        p.setDescription(desc);
        p.setStatus(EntityStatus.ACTIVE);
        HProject saved = projectRepository.save(p);
        // HProject.projectIterations has no cascade = PERSIST, so the
        // iteration has to be saved through its own repository.
        HProjectIteration v = new HProjectIteration();
        v.setSlug("master");
        v.setStatus(EntityStatus.ACTIVE);
        v.setProject(saved);
        iterationRepository.save(v);
        saved.setProjectIterations(new java.util.ArrayList<>(List.of(v)));
    }

    private void saveLocale(String id, String display, String nativeName,
                            boolean enabledByDefault) {
        HLocale l = new HLocale(new LocaleId(id), enabledByDefault, true);
        l.setDisplayName(display);
        l.setNativeName(nativeName);
        localeRepository.save(l);
    }
}
