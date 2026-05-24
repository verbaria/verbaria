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
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HApplicationConfiguration;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ApplicationConfigurationRepository;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.repository.RoleRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;

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
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final ApplicationConfigurationRepository configRepository;
    private final PasswordEncoder passwordEncoder;

    public DevSeedData(ProjectRepository projectRepository,
                       LocaleRepository localeRepository,
                       ProjectIterationRepository iterationRepository,
                       AccountRepository accountRepository,
                       RoleRepository roleRepository,
                       DocumentRepository documentRepository,
                       TextFlowRepository textFlowRepository,
                       TextFlowTargetRepository textFlowTargetRepository,
                       ApplicationConfigurationRepository configRepository,
                       PasswordEncoder passwordEncoder) {
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.iterationRepository = iterationRepository;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.configRepository = configRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRolesAndAccounts();
        seedProjects();
        seedLocales();
        seedDocuments();
    }

    private void seedRolesAndAccounts() {
        HAccountRole admin = roleRepository.findByName("admin").orElseGet(() -> saveRole("admin"));
        HAccountRole user  = roleRepository.findByName("user").orElseGet(() -> saveRole("user"));
        // Deterministic API keys so the CLI smoke test can authenticate
        // without UI clicks. Override in production.
        ensureAccount("admin",   "admin", "Administrator",    "admin@example.com",            Set.of(admin, user), "b6d7044e9ee3b720c81dba7e3ea53d56");
        ensureAccount("dev",     "dev",   "Dev User",         "dev@example.com",              Set.of(user),        "11111111111111111111111111111111");
        ensureAccount("vistall", "1234",  "Valery Semenchuk", "vistall.valeriy@gmail.com",    Set.of(admin, user), "22222222222222222222222222222222");
        log.info("Seeded {} roles + {} accounts", roleRepository.count(), accountRepository.count());
    }

    private void ensureAccount(String username, String password, String fullName,
                               String email, Set<HAccountRole> roles, String apiKey) {
        var existing = accountRepository.findByUsername(username);
        if (existing.isPresent()) {
            // Backfill apiKey on already-seeded rows so the CLI smoke test
            // works after the dev DB has been around for a while.
            if (existing.get().getApiKey() == null || existing.get().getApiKey().isBlank()) {
                existing.get().setApiKey(apiKey);
                accountRepository.save(existing.get());
            }
            return;
        }
        saveAccount(username, password, fullName, email, roles, apiKey);
    }

    private HAccountRole saveRole(String name) {
        HAccountRole r = new HAccountRole();
        r.setName(name);
        return roleRepository.save(r);
    }

    private void saveAccount(String username, String password, String fullName,
                             String email, Set<HAccountRole> roles, String apiKey) {
        HAccount a = new HAccount();
        a.setUsername(username);
        a.setPasswordHash(passwordEncoder.encode(password));
        a.setEnabled(true);
        a.setApiKey(apiKey);
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
        saveLocale("en-US", "English (US)",        "English",     true);
        saveLocale("fr",    "French",              "Français",    true);
        saveLocale("de",    "German",              "Deutsch",     true);
        saveLocale("es",    "Spanish",             "Español",     true);
        saveLocale("it",    "Italian",             "Italiano",    true);
        saveLocale("pt",    "Portuguese",          "Português",   true);
        saveLocale("pt-BR", "Brazilian Portuguese","Português (Brasil)", true);
        saveLocale("ru",    "Russian",             "Русский",     true);
        saveLocale("uk",    "Ukrainian",           "Українська",  true);
        saveLocale("pl",    "Polish",              "Polski",      true);
        saveLocale("nl",    "Dutch",               "Nederlands",  true);
        saveLocale("ja",    "Japanese",            "日本語",        true);
        saveLocale("zh-CN", "Simplified Chinese",  "简体中文",      true);
        saveLocale("zh-TW", "Traditional Chinese", "繁體中文",      true);
        saveLocale("ko",    "Korean",              "한국어",        true);
        saveLocale("vi-VN", "Vietnamese (Vietnam)", "Tiếng Việt", true);
        saveLocale("th",    "Thai",                "ไทย",          true);
        saveLocale("tr-TR", "Turkish (Turkey)",    "Türkçe",      true);
        saveLocale("ar",    "Arabic",              "العربية",      true);
        saveLocale("he",    "Hebrew",              "עברית",        true);
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
        LocaleId localeId = new LocaleId(id);
        if (localeRepository.findByLocaleId(localeId).isPresent()) return;
        HLocale l = new HLocale(localeId, enabledByDefault, true);
        l.setDisplayName(display);
        l.setNativeName(nativeName);
        localeRepository.save(l);
    }

    private static final List<String> SAMPLE_SOURCES = List.of(
            "Hello, world!",
            "Welcome to the project.",
            "Save changes",
            "Cancel",
            "Translation Memory is enabled",
            "Please enter your name",
            "An error occurred while saving the file. Please try again.",
            "Settings"
    );

    private void seedDocuments() {
        HLocale enUs = localeRepository.findByLocaleId(new LocaleId("en-US"))
                .orElse(null);
        HLocale fr = localeRepository.findByLocaleId(new LocaleId("fr")).orElse(null);
        HLocale de = localeRepository.findByLocaleId(new LocaleId("de")).orElse(null);
        HLocale ru = localeRepository.findByLocaleId(new LocaleId("ru")).orElse(null);
        if (enUs == null) {
            log.warn("en-US locale missing, skipping document seed");
            return;
        }

        int docCount = 0;
        int flowCount = 0;
        int targetCount = 0;

        for (HProject project : projectRepository.findAll()) {
            for (HProjectIteration iteration : project.getProjectIterations()) {
                List<HDocument> existing = documentRepository
                        .findByVersion(project.getSlug(), iteration.getSlug());
                if (!existing.isEmpty()) continue;

                HDocument doc = new HDocument(
                        "messages", "messages", "",
                        ContentType.TextPlain, enUs);
                doc.setProjectIteration(iteration);
                doc.setRevision(1);

                List<HTextFlow> flows = new java.util.ArrayList<>();
                for (int i = 0; i < SAMPLE_SOURCES.size(); i++) {
                    String src = SAMPLE_SOURCES.get(i);
                    HTextFlow tf = new HTextFlow(doc, "msg." + i, src);
                    tf.setRevision(1);
                    tf.setPos(i);
                    flows.add(tf);
                }
                doc.setTextFlows(flows);
                iteration.getDocuments().put(doc.getDocId(), doc);

                HDocument savedDoc = documentRepository.save(doc);
                docCount++;
                flowCount += flows.size();

                List<HTextFlow> persistedFlows = textFlowRepository
                        .findByDocument(savedDoc.getId());

                if (fr != null) {
                    targetCount += seedTargets(persistedFlows, fr,
                            ContentState.Translated, "FR: ", 5);
                }
                if (de != null) {
                    targetCount += seedTargets(persistedFlows, de,
                            ContentState.Approved, "DE: ", 3);
                }
                if (ru != null) {
                    targetCount += seedTargets(persistedFlows, ru,
                            ContentState.NeedReview, "RU: ", 2);
                }
            }
        }
        log.info("Seeded {} documents with {} text flows and {} translation targets",
                docCount, flowCount, targetCount);
    }

    private int seedTargets(List<HTextFlow> flows, HLocale locale,
                            ContentState state, String prefix, int count) {
        int n = Math.min(count, flows.size());
        for (int i = 0; i < n; i++) {
            HTextFlow tf = flows.get(i);
            String source = tf.getContents().isEmpty() ? "" : tf.getContents().get(0);
            HTextFlowTarget target = new HTextFlowTarget(tf, locale);
            target.setContents(List.of(prefix + source));
            target.setState(state);
            target.setTextFlowRevision(tf.getRevision());
            textFlowTargetRepository.save(target);
        }
        return n;
    }
}
