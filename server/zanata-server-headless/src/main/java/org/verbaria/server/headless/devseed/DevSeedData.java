package org.verbaria.server.headless.devseed;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.model.*;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.verbaria.server.headless.repository.*;
import org.zanata.rest.dto.extensions.comment.SimpleComment;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.verbaria.server.headless.security.Roles;
import org.zanata.util.HashUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Inserts a handful of demo projects on startup so the explore screen has
 * something to render. Active only when the {@code dev} Spring profile is
 * enabled — run with {@code -Dspring.profiles.active=dev} or the
 * {@code SPRING_PROFILES_ACTIVE=dev} env var.
 */
@Component
@Profile("dev")
@Order(100)
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
    private final TextFlowExtensionStore extensionStore;
    @PersistenceContext
    private EntityManager entityManager;

    public DevSeedData(ProjectRepository projectRepository,
                       LocaleRepository localeRepository,
                       ProjectIterationRepository iterationRepository,
                       AccountRepository accountRepository,
                       RoleRepository roleRepository,
                       DocumentRepository documentRepository,
                       TextFlowRepository textFlowRepository,
                       TextFlowTargetRepository textFlowTargetRepository,
                       ApplicationConfigurationRepository configRepository,
                       PasswordEncoder passwordEncoder,
                       TextFlowExtensionStore extensionStore) {
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
        this.extensionStore = extensionStore;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedRolesAndAccounts();
        seedProjects();
        seedLocales();
        seedDocuments();
        seedHomePage();
        migrateRawResIdsToHash();
    }

    /**
     * One-shot data migration: legacy / previously-seeded HTextFlow rows
     * stored the raw property key (e.g. "msg.0") as resId. Hash them.
     * <p>
     * Uses native SQL because HTextFlow.resId is annotated {@code @NaturalId}
     * which Hibernate treats as immutable on managed entities — JPA updates
     * to the column throw {@code HibernateException: An immutable attribute
     * [resId] within compound natural identifier ... was altered}. Native
     * SQL bypasses that check.
     * <p>
     * Targets/history reference the parent HTextFlow by FK to HTextFlow.id,
     * not resId, so renaming the resId is safe.
     */
    private void migrateRawResIdsToHash() {
        // Find rows whose res_id isn't already a 32-char lowercase hex hash.
        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery("""
            SELECT id, res_id
            FROM htext_flow
            WHERE res_id !~ '^[0-9a-f]{32}$'
            """).getResultList();
        if (rows.isEmpty()) {
            return;
        }

        int migrated = 0;
        for (Object[] row : rows) {
            Long id = ((Number) row[0]).longValue();
            String originalKey = (String) row[1];
            String hashed = HashUtil.generateHash(originalKey);

            entityManager.createNativeQuery(
                    "UPDATE htext_flow SET res_id = :h WHERE id = :id")
                .setParameter("h", hashed)
                .setParameter("id", id)
                .executeUpdate();
            migrated++;
        }
        log.info("Migrated {} HTextFlow rows to hashed resIds", migrated);
    }

    /**
     * Sample Markdown for the public home page at "/" — so the new HomeView
     * has something to render out of the box. Idempotent: only inserts the
     * row when one doesn't already exist (admin edits via /admin/home-content
     * survive restarts).
     */
    private void seedHomePage() {
        if (configRepository.findByKey(HApplicationConfiguration.KEY_HOME_CONTENT).isPresent()) {
            return;
        }
        HApplicationConfiguration row = new HApplicationConfiguration();
        row.setKey(HApplicationConfiguration.KEY_HOME_CONTENT);
        row.setValue("""
            # Welcome to Zanata

            Zanata is a web-based translation management system.
            Translators work in the browser; developers push source
            strings and pull translations via the CLI or Maven plugin.

            ## Get started

            * Browse [projects](/projects) and translation activity
            * Sign in to start translating
            * Admins can edit this page from the **Administration → Home page content** screen

            _This text is stored as Markdown in the `pages.home.content`
            application-configuration row and rendered through CommonMark + OWASP
            HTML sanitiser, so anything an admin pastes is safe to display._
            """);
        configRepository.save(row);
        log.info("Seeded default home page Markdown");
    }

    private void seedRolesAndAccounts() {
        HAccountRole admin = roleRepository.findByName(Roles.ADMIN).orElseThrow();
        HAccountRole user = roleRepository.findByName(Roles.USER).orElseThrow();

        ensureAccount("admin", "admin", "Administrator", "admin@example.com", Set.of(admin, user), "22222222222222222222222222222222");
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
        // Upsert by slug (no early-return on a non-empty DB) so edits to this
        // seed — new projects, names/groups, source URLs — re-apply on restart.
        // Ungrouped projects (no "/" in the name) — shown at the tree top level.
        saveProject("about-fedora", "About Fedora",
            "Translations for the About Fedora release notes module.", null);
        saveProject("rhel-installation-guide", "RHEL Installation Guide",
            "Red Hat Enterprise Linux installation documentation.", null);
        saveProject("gnome-shell", "GNOME Shell",
            "Translatable strings for the GNOME desktop shell.",
            "https://gitlab.gnome.org/GNOME/gnome-shell");
        saveProject("zanata-platform", "Zanata Platform",
            "Localization platform UI strings (meta!).",
            "https://github.com/verbaria/verbaria");

        // Grouped projects: the slug stays the stable routing/match key, while
        // the "group/leaf" name drives the Projects tree grouping. Here the
        // "consulo" group holds several plugins, with a nested "extensions"
        // sub-group, e.g. consulo/consulo-java and consulo/extensions/markdown.
        saveProject("consulo", "consulo/consulo",
            "Consulo platform core strings.",
            "https://github.com/consulo/consulo");
        saveProject("consulo-java", "consulo/consulo-java",
            "Java language plugin.",
            "https://github.com/consulo/consulo-java");
        saveProject("consulo-python", "consulo/consulo-python",
            "Python language plugin.",
            "https://github.com/consulo/consulo-python");
        saveProject("consulo-csharp", "consulo/consulo-csharp",
            "C# language plugin.",
            "https://github.com/consulo/consulo-csharp");
        saveProject("consulo-markdown", "consulo/extensions/markdown",
            "Markdown extension.",
            "https://github.com/consulo/consulo-markdown");
        saveProject("consulo-yaml", "consulo/extensions/yaml",
            "YAML extension.",
            "https://github.com/consulo/consulo-yaml");

        saveProject("perf-1k", "perf/1k strings",
            "Performance test project: one document with 1000 source strings.",
            null);

        log.info("Inserted {} demo projects", projectRepository.count());
    }

    private void seedLocales() {
        saveLocale("en-US", "English (US)", "English", true);
        saveLocale("fr", "French", "Français", true);
        saveLocale("de", "German", "Deutsch", true);
        saveLocale("es", "Spanish", "Español", true);
        saveLocale("it", "Italian", "Italiano", true);
        saveLocale("pt", "Portuguese", "Português", true);
        saveLocale("pt-BR", "Brazilian Portuguese", "Português (Brasil)", true);
        saveLocale("ru", "Russian", "Русский", true);
        saveLocale("uk", "Ukrainian", "Українська", true);
        saveLocale("pl", "Polish", "Polski", true);
        saveLocale("nl", "Dutch", "Nederlands", true);
        saveLocale("ja", "Japanese", "日本語", true);
        saveLocale("zh-CN", "Simplified Chinese", "简体中文", true);
        saveLocale("zh-TW", "Traditional Chinese", "繁體中文", true);
        saveLocale("ko", "Korean", "한국어", true);
        saveLocale("vi-VN", "Vietnamese (Vietnam)", "Tiếng Việt", true);
        saveLocale("th", "Thai", "ไทย", true);
        saveLocale("tr-TR", "Turkish (Turkey)", "Türkçe", true);
        saveLocale("ar", "Arabic", "العربية", true);
        saveLocale("he", "Hebrew", "עברית", true);
        log.info("Inserted {} demo locales", localeRepository.count());
    }

    private void saveProject(String slug, String name, String desc,
                             String sourceViewUrl) {
        HProject p = projectRepository.findBySlug(slug).orElseGet(HProject::new);
        p.setSlug(slug);
        p.setName(name);
        p.setDescription(desc);
        if (sourceViewUrl != null) {
            p.setSourceViewURL(sourceViewUrl);
        }
        p.setStatus(EntityStatus.ACTIVE);
        HProject saved = projectRepository.save(p);
        // HProject.projectIterations has no cascade = PERSIST, so the iteration
        // is saved through its own repository — only when not already present.
        if (iterationRepository.findByProjectAndSlug(slug, "master").isEmpty()) {
            HProjectIteration v = new HProjectIteration();
            v.setSlug("master");
            v.setStatus(EntityStatus.ACTIVE);
            saved.addIteration(v);
            iterationRepository.save(v);
        }
    }

    private void saveLocale(String id, String display, String nativeName,
                            boolean enabledByDefault) {
        LocaleId localeId = new LocaleId(id);
        if (localeRepository.findByLocaleId(localeId).isPresent()) {
            return;
        }
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

    private static final List<String> ERROR_SOURCES = List.of(
        "File not found",
        "Permission denied",
        "Connection timed out",
        "Invalid input value",
        "Operation was cancelled",
        "Out of memory"
    );

    private static final List<String> MENU_SOURCES = List.of(
        "File",
        "Edit",
        "View",
        "Open Recent",
        "Preferences",
        "Help"
    );

    /** One seedable document: its docId and the source strings it contains. */
    private record DocSeed(String docId, List<String> sources) {}

    /** Documents to seed for a project — a few per project so the doc switcher
     *  has something to switch between; perf-1k stays a single 1000-string doc. */
    private List<DocSeed> docsFor(String slug) {
        if ("perf-1k".equals(slug)) {
            return List.of(new DocSeed("messages", perfSources()));
        }
        return List.of(
            new DocSeed("messages", SAMPLE_SOURCES),
            new DocSeed("errors", ERROR_SOURCES),
            new DocSeed("menu", MENU_SOURCES));
    }

    private List<String> perfSources() {
        List<String> out = new ArrayList<>(1000);
        for (int i = 0; i < 1000; i++) {
            out.add(SAMPLE_SOURCES.get(i % SAMPLE_SOURCES.size())
                + " #" + (i + 1));
        }
        return out;
    }

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
                for (DocSeed ds : docsFor(project.getSlug())) {
                    if (documentRepository.findByVersionAndDocId(
                            project.getSlug(), iteration.getSlug(), ds.docId())
                            .isPresent()) {
                        continue;
                    }

                    HDocument doc = new HDocument(
                        ds.docId(), ds.docId(), "",
                        ContentType.TextPlain, enUs);
                    doc.setProjectIteration(iteration);
                    doc.setRevision(1);

                    List<String> sources = ds.sources();
                    List<HTextFlow> flows = new ArrayList<>();
                    for (int i = 0; i < sources.size(); i++) {
                        String src = sources.get(i);
                        // Match production behavior: resId is a hash, original
                        // human-readable key (msg.N) lives in the gettext context.
                        String originalKey = "msg." + i;
                        HTextFlow tf = new HTextFlow(doc,
                            HashUtil.generateHash(originalKey), src);
                        tf.setRevision(1);
                        tf.setPos(i);
                        PotEntryHeader header =
                            new PotEntryHeader();
                        header.setContext(originalKey);
                        extensionStore.put(tf, header);
                        SimpleComment comment = new SimpleComment(
                            "Demo source comment for " + originalKey);
                        extensionStore.put(tf, comment);
                        flows.add(tf);
                    }
                    doc.setTextFlows(flows);
                    iteration.getDocuments().put(doc.getDocId(), doc);

                    HDocument savedDoc = documentRepository.save(doc);
                    docCount++;
                    flowCount += flows.size();

                    List<HTextFlow> persistedFlows = textFlowRepository
                        .findByDocument(savedDoc.getId());

                    boolean big = persistedFlows.size() >= 1000;
                    if (fr != null) {
                        targetCount += seedTargets(persistedFlows, fr,
                            ContentState.Translated, "FR: ", big ? 600 : 5);
                    }
                    if (de != null) {
                        targetCount += seedTargets(persistedFlows, de,
                            ContentState.Approved, "DE: ", big ? 400 : 3);
                    }
                    if (ru != null) {
                        targetCount += seedTargets(persistedFlows, ru,
                            ContentState.NeedReview, "RU: ", big ? 300 : 2);
                    }
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
