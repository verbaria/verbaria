package org.verbaria.it;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;

import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.RoleRepository;
import org.verbaria.server.headless.security.Roles;

@Service
public class ItFixtures {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final LocaleRepository localeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;

    public ItFixtures(AccountRepository accountRepository,
                      RoleRepository roleRepository,
                      LocaleRepository localeRepository,
                      ProjectRepository projectRepository,
                      ProjectIterationRepository iterationRepository) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.localeRepository = localeRepository;
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
    }

    @Transactional
    public HLocale ensureLocale(String localeId) {
        LocaleId lid = new LocaleId(localeId);
        return localeRepository.findByLocaleId(lid).orElseGet(
                () -> localeRepository.save(new HLocale(lid, true, true)));
    }

    @Transactional
    public void ensureAdmin(String username, String apiKey) {
        if (accountRepository.findByUsername(username).isPresent()) {
            return;
        }
        HAccountRole admin = roleRepository.findByName(Roles.ADMIN).orElseThrow();
        HAccountRole user = roleRepository.findByName(Roles.USER).orElseThrow();
        HAccount account = new HAccount();
        account.setUsername(username);
        account.setPasswordHash("x");
        account.setEnabled(true);
        account.setApiKey(apiKey);
        account.setRoles(new HashSet<>(Set.of(admin, user)));
        HPerson person = new HPerson();
        person.setName(username);
        person.setEmail(username + "@it.local");
        person.setAccount(account);
        account.setPerson(person);
        accountRepository.save(account);
    }

    @Transactional
    public void ensureProject(String slug, String version) {
        HProject project = projectRepository.findBySlug(slug).orElseGet(() -> {
            HProject p = new HProject();
            p.setSlug(slug);
            p.setName(slug);
            p.setStatus(EntityStatus.ACTIVE);
            p.setDefaultProjectType(ProjectType.File);
            return projectRepository.save(p);
        });
        if (iterationRepository.findByProjectAndSlug(slug, version).isEmpty()) {
            HProjectIteration iter = new HProjectIteration();
            iter.setSlug(version);
            iter.setProject(project);
            iter.setStatus(EntityStatus.ACTIVE);
            iter.setProjectType(ProjectType.File);
            project.addIteration(iter);
            iterationRepository.save(iter);
        }
    }
}
