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
import org.verbaria.server.headless.service.ProjectHierarchyService;

@Service
public class ItFixtures {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final LocaleRepository localeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final ProjectHierarchyService hierarchyService;

    public ItFixtures(AccountRepository accountRepository,
                      RoleRepository roleRepository,
                      LocaleRepository localeRepository,
                      ProjectRepository projectRepository,
                      ProjectIterationRepository iterationRepository,
                      ProjectHierarchyService hierarchyService) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.localeRepository = localeRepository;
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.hierarchyService = hierarchyService;
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

    /** A plain (non-admin, non-reviewer) account. */
    @Transactional
    public void ensureUser(String username, String apiKey) {
        if (accountRepository.findByUsername(username).isPresent()) {
            return;
        }
        HAccountRole user = roleRepository.findByName(Roles.USER).orElseThrow();
        HAccount account = new HAccount();
        account.setUsername(username);
        account.setPasswordHash("x");
        account.setEnabled(true);
        account.setApiKey(apiKey);
        account.setRoles(new HashSet<>(Set.of(user)));
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

    /** A project with no versions yet (for testing version mirroring). */
    @Transactional
    public void ensureProjectNoVersion(String slug, ProjectType type) {
        projectRepository.findBySlug(slug).orElseGet(() -> {
            HProject p = new HProject();
            p.setSlug(slug);
            p.setName(slug);
            p.setStatus(EntityStatus.ACTIVE);
            p.setDefaultProjectType(type);
            return projectRepository.save(p);
        });
    }

    /** Turn on overrideLocales with the given customized locale set. */
    @Transactional
    public void setProjectLocales(String slug, String... localeIds) {
        HProject p = projectRepository.findBySlug(slug).orElseThrow();
        Set<HLocale> set = new HashSet<>();
        for (String id : localeIds) {
            set.add(ensureLocale(id));
        }
        p.setOverrideLocales(true);
        p.setCustomizedLocales(set);
        projectRepository.save(p);
    }

    @Transactional
    public void setProjectSourceViewURL(String slug, String url) {
        HProject p = projectRepository.findBySlug(slug).orElseThrow();
        p.setSourceViewURL(url);
        projectRepository.save(p);
    }

    @Transactional
    public void setProjectSourceLocale(String slug, String localeId) {
        HProject p = projectRepository.findBySlug(slug).orElseThrow();
        p.setDefaultSourceLocale(ensureLocale(localeId));
        projectRepository.save(p);
    }

    /** Link child -> parent, mirroring the parent's versions onto the child. */
    @Transactional
    public void linkParent(String childSlug, String parentSlug) {
        HProject child = projectRepository.findBySlug(childSlug).orElseThrow();
        HProject parent = projectRepository.findBySlug(parentSlug).orElseThrow();
        hierarchyService.linkParent(child, parent);
    }

    /** Add a version whose iteration carries no own project type. */
    @Transactional
    public void addVersion(String slug, String version) {
        HProject p = projectRepository.findBySlug(slug).orElseThrow();
        if (iterationRepository.findByProjectAndSlug(slug, version).isPresent()) {
            return;
        }
        HProjectIteration iter = new HProjectIteration();
        iter.setSlug(version);
        iter.setProject(p);
        iter.setStatus(EntityStatus.ACTIVE);
        p.addIteration(iter);
        iterationRepository.save(iter);
    }

    /** Add a parent version and propagate it to children (mirrors production). */
    @Transactional
    public void addVersionAndPropagate(String parentSlug, String version) {
        addVersion(parentSlug, version);
        HProject parent = projectRepository.findBySlug(parentSlug).orElseThrow();
        hierarchyService.propagateVersionToChildren(parent, version);
    }

    @Transactional
    public boolean hasVersion(String slug, String version) {
        return iterationRepository.findByProjectAndSlug(slug, version).isPresent();
    }
}
