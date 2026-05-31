package org.zanata.spring.web.rest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import jakarta.persistence.EntityNotFoundException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.ContentState;
import org.zanata.common.ContentType;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.common.ResourceType;
import org.zanata.common.TransUnitCount;
import org.zanata.common.TransUnitWords;
import org.zanata.model.HAccount;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HPerson;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.po.HPotEntryData;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.ProcessStatus;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.ProjectIteration;
import org.zanata.rest.dto.VersionInfo;
import org.zanata.rest.dto.extensions.consulo.ConsuloSubFile;
import org.zanata.rest.dto.extensions.gettext.PotEntryHeader;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.rest.dto.stats.ContainerTranslationStatistics;
import org.zanata.rest.dto.stats.TranslationStatistics;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.service.DocumentImportService;

/**
 * Compatibility REST bridge for the classic Zanata CLI
 * (zanata-cli / zanata-rest-client / zanata-client-commands).
 *
 * The CLI talks to a fixed set of paths under {@code /rest/...} which the
 * old JAX-RS services (in zanata-war / services) used to publish. Those are
 * gone in this Spring Boot module, so this controller stands those routes
 * back up using the existing Spring Data repositories.
 *
 * Notes on overlapping routes:
 *  - {@link ProjectsApiController} already exposes
 *    GET /rest/projects/p/{slug} as a free-form Map<String,Object> for the
 *    React UI. To avoid clobbering it, the CLI-shape Project DTO endpoint
 *    here is registered under the same path but typed differently — Spring
 *    will dispatch by HTTP method and accept header. The PUT method is
 *    exclusive to this controller; the existing GET still works for the React
 *    UI but if/when ProjectsApiController is deleted this controller's GET
 *    will take over transparently.
 *  - {@link EditorController} exposes /rest/project/{slug}/... (singular) for
 *    the React editor; CLI paths are /rest/projects/p/{slug}/... (plural) so
 *    there is no collision.
 *
 * Security: SecurityConfig currently permits all /rest/** requests. Write
 * endpoints here additionally read SecurityContextHolder and reject anonymous
 * principals with 401 — once an API-key filter is added, this will Just Work
 * because Spring will populate the authentication for the matching credentials.
 */
@RestController
@RequestMapping("/rest")
public class ZanataCliBridgeController {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ZanataCliBridgeController.class);

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final DocumentImportService importService;
    private final org.zanata.spring.service.OfflineExportService offlineExportService;
    private final org.zanata.spring.service.SourceUploadService sourceUploadService;

    @Value("${spring.application.version:unknown}")
    private String applicationVersion;

    public ZanataCliBridgeController(ProjectRepository projectRepository,
                                     ProjectIterationRepository iterationRepository,
                                     DocumentRepository documentRepository,
                                     TextFlowRepository textFlowRepository,
                                     TextFlowTargetRepository textFlowTargetRepository,
                                     LocaleRepository localeRepository,
                                     AccountRepository accountRepository,
                                     DocumentImportService importService,
                                     org.zanata.spring.service.OfflineExportService offlineExportService,
                                     org.zanata.spring.service.SourceUploadService sourceUploadService) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
        this.importService = importService;
        this.offlineExportService = offlineExportService;
        this.sourceUploadService = sourceUploadService;
    }

    // ---------- 1. version ----------

    @GetMapping("/version")
    public ResponseEntity<VersionInfo> version() {
        return ResponseEntity.ok(new VersionInfo(
                applicationVersion,
                Instant.now().toString(),
                "spring"));
    }

    // ---------- 1a. list all projects (CLI init flow) ----------
    //
    // The CLI's `zanata-cli init` calls GET /rest/projects when the user picks
    // "select an existing project". JSON only.

    @GetMapping(value = "/projects",
            produces = {MediaType.APPLICATION_JSON_VALUE,
                        "application/vnd.zanata.projects+json"})
    @Transactional(readOnly = true)
    public ResponseEntity<?> listProjects() {
        List<Project> dtos = new ArrayList<>();
        for (HProject p : projectRepository.findAll()) {
            // Skip soft-deleted projects so the CLI menu stays clean.
            if (p.getStatus() == EntityStatus.OBSOLETE) continue;
            Project dto = new Project();
            dto.setId(p.getSlug());
            dto.setName(p.getName() == null ? p.getSlug() : p.getName());
            dto.setDescription(p.getDescription());
            dto.setStatus(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
            dto.setDefaultType(p.getDefaultProjectType() == null
                    ? ProjectType.File.toString()
                    : p.getDefaultProjectType().toString());
            // Iterations omitted on purpose — the CLI init only needs id/name
            // for the picker, and including them blows up the response.
            dtos.add(dto);
        }
        return ResponseEntity.ok(dtos);
    }

    // ---------- 2. get project (CLI Project DTO) ----------

    @GetMapping("/projects/p/{slug}/cli")
    @Transactional(readOnly = true)
    public ResponseEntity<Project> getProjectCli(@PathVariable("slug") String slug) {
        // Sibling path '/cli' chosen to avoid clobbering ProjectsApiController.
        // (See class-level Javadoc.) The legacy CLI hits /projects/p/{slug} directly,
        // so we also expose the same content via PUT /projects/p/{slug} below;
        // if ProjectsApiController is deleted, change this mapping to "/projects/p/{slug}".
        return projectRepository.findBySlugWithIterations(slug)
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---------- 3. put project ----------

    @PutMapping("/projects/p/{slug}")
            
    @Transactional
    public ResponseEntity<Project> putProject(@PathVariable("slug") String slug,
                                              @RequestBody Project body) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<HProject> existing = projectRepository.findBySlug(slug);
        HProject p;
        boolean created;
        if (existing.isPresent()) {
            p = existing.get();
            created = false;
        } else {
            p = new HProject();
            p.setSlug(slug);
            p.setStatus(EntityStatus.ACTIVE);
            p.setDefaultProjectType(ProjectType.File);
            created = true;
        }
        if (body.getName() != null) p.setName(body.getName());
        if (body.getDescription() != null) p.setDescription(body.getDescription());
        if (body.getSourceViewURL() != null) p.setSourceViewURL(body.getSourceViewURL());
        if (body.getSourceCheckoutURL() != null) p.setSourceCheckoutURL(body.getSourceCheckoutURL());
        if (body.getStatus() != null) p.setStatus(body.getStatus());
        if (body.getDefaultType() != null) {
            try {
                p.setDefaultProjectType(ProjectType.getValueOf(body.getDefaultType()));
            } catch (Exception ignore) {
                // keep whatever default we had
            }
        }
        // HProject must have a name (NotEmpty); fall back to the slug.
        if (p.getName() == null || p.getName().isEmpty()) {
            p.setName(slug);
        }
        HProject saved = projectRepository.save(p);
        Project out = toDto(saved);
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                : ResponseEntity.ok(out);
    }

    // ---------- 4. get project iteration ----------

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectIteration> getIteration(@PathVariable("slug") String slug,
                                                         @PathVariable("iter") String iter) {
        return iterationRepository.findByProjectAndSlug(slug, iter)
                .map(i -> ResponseEntity.ok(toDto(i)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---------- 4b. zanata.xml sample (CLI init step "Continue?") ----------
    //
    // Ports the legacy ConfigurationServiceImpl.getGeneralConfig builder
    // (server/services/.../ConfigurationServiceImpl.java in the old repo)
    // so `zanata-cli init` can fetch a starter zanata.xml after the user
    // picks project+version.

    @GetMapping(value = "/projects/p/{slug}/iterations/i/{iter}/config",
            produces = {MediaType.APPLICATION_JSON_VALUE,
                        "application/vnd.zanata.project.iteration+json"})
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> sampleConfiguration(
            @PathVariable("slug") String slug,
            @PathVariable("iter") String iter,
            @RequestHeader(value = "Host", required = false) String host,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(value = "X-Forwarded-Proto", required = false) String forwardedProto) {
        var iteration = iterationRepository.findByProjectAndSlug(slug, iter);
        if (iteration.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        ProjectType pt = iteration.get().getProjectType();
        if (pt == null && iteration.get().getProject() != null) {
            pt = iteration.get().getProject().getDefaultProjectType();
        }
        // Starter verbaria.json. The CLI's `init` fills in srcDir/includes/
        // excludes/transDir afterward. Keys match org.zanata.client.config
        // .ZanataConfig so the client can parse it straight back as JSON.
        Map<String, Object> config = new java.util.LinkedHashMap<>();
        config.put("url", resolveBaseUrl(host, forwardedHost, forwardedProto));
        config.put("project", slug);
        config.put("projectVersion", iter);
        if (pt != null) {
            config.put("projectType", pt.toString().toLowerCase());
        }
        return ResponseEntity.ok(config);
    }

    private static String resolveBaseUrl(String host, String forwardedHost, String forwardedProto) {
        String h = (forwardedHost != null && !forwardedHost.isBlank()) ? forwardedHost : host;
        if (h == null || h.isBlank()) h = "localhost:8080";
        String scheme = (forwardedProto != null && !forwardedProto.isBlank())
                ? forwardedProto : "http";
        return scheme + "://" + h + "/";
    }

    // ---------- 4a. iteration active locales ----------
    //
    // The CLI's push command consults this to decide which translation files
    // to upload — files for locales not in this list are skipped with
    // "no locale entry found from project config". We return every enabled
    // locale from the server (no per-iteration locale overrides yet).

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/locales")
    @Transactional(readOnly = true)
    public ResponseEntity<?> iterationLocales(@PathVariable("slug") String slug,
                                              @PathVariable("iter") String iter) {
        var iterOpt = iterationRepository.findByProjectAndSlug(slug, iter);
        if (iterOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Project-customized locale set (when overrideLocales=true) → returns
        // only those locales; else fall back to every active server locale.
        var customized = iterationRepository.findProjectLocales(iterOpt.get().getId());
        Iterable<org.zanata.model.HLocale> source = customized.isPresent()
                ? customized.get() : localeRepository.findAll();
        // Always include the project's source locale so the CLI can write
        // source-edit pushes back even when it's not in customizedLocales.
        var projectSource = iterationRepository.findProjectSourceLocale(iterOpt.get().getId());
        java.util.LinkedHashMap<Long, org.zanata.model.HLocale> uniq = new java.util.LinkedHashMap<>();
        for (var l : source) if (l.isActive()) uniq.put(l.getId(), l);
        projectSource.filter(org.zanata.model.HLocale::isActive)
                .ifPresent(l -> uniq.putIfAbsent(l.getId(), l));
        List<org.zanata.rest.dto.LocaleDetails> details = new ArrayList<>();
        for (var loc : uniq.values()) {
            if (loc.getLocaleId() == null) continue;
            org.zanata.rest.dto.LocaleDetails ld = new org.zanata.rest.dto.LocaleDetails(
                    loc.getLocaleId(),
                    loc.getDisplayName(),
                    null /* alias */,
                    loc.getNativeName(),
                    true /* enabled */,
                    true /* enabledByDefault */,
                    loc.getPluralForms(),
                    false /* rtl - not on HLocale */);
            details.add(ld);
        }
        return ResponseEntity.ok(details);
    }

    // ---------- 5. put project iteration ----------

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}")
            
    @Transactional
    public ResponseEntity<ProjectIteration> putIteration(@PathVariable("slug") String slug,
                                                         @PathVariable("iter") String iter,
                                                         @RequestBody ProjectIteration body) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        HProject project = projectRepository.findBySlug(slug).orElse(null);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }
        Optional<HProjectIteration> existing =
                iterationRepository.findByProjectAndSlug(slug, iter);
        HProjectIteration i;
        boolean created;
        if (existing.isPresent()) {
            i = existing.get();
            created = false;
        } else {
            i = new HProjectIteration();
            i.setSlug(iter);
            i.setProject(project);
            i.setStatus(EntityStatus.ACTIVE);
            project.addIteration(i);
            created = true;
        }
        if (body.getStatus() != null) i.setStatus(body.getStatus());
        if (body.getProjectType() != null) {
            try {
                i.setProjectType(ProjectType.getValueOf(body.getProjectType()));
            } catch (Exception ignore) {
                // inherit from project
            }
        }
        HProjectIteration saved = iterationRepository.save(i);
        ProjectIteration out = toDto(saved);
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                : ResponseEntity.ok(out);
    }

    // ---------- 6. list source documents ----------

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/r")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listSourceDocs(@PathVariable("slug") String slug,
                                            @PathVariable("iter") String iter) {
        if (iterationRepository.findByProjectAndSlug(slug, iter).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // CLI client (SourceDocResourceClient.getResourceMeta) expects a bare
        // List<ResourceMeta> in JSON.
        List<ResourceMeta> out = new ArrayList<>();
        for (HDocument d : documentRepository.findByVersion(slug, iter)) {
            ResourceMeta meta = new ResourceMeta(d.getDocId());
            meta.setContentType(d.getContentType() == null
                    ? ContentType.TextPlain : d.getContentType());
            meta.setLang(d.getLocale() == null || d.getLocale().getLocaleId() == null
                    ? LocaleId.EN_US : d.getLocale().getLocaleId());
            meta.setType(ResourceType.FILE);
            meta.setRevision(d.getRevision());
            out.add(meta);
        }
        return ResponseEntity.ok(out);
    }

    // ---------- 7. get a source document ----------

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getSourceDoc(@PathVariable("slug") String slug,
                                                 @PathVariable("iter") String iter,
                                                 @PathVariable("docId") String docId) {
        String realDocId = decodeDocId(docId);
        return documentRepository.findByVersionAndDocId(slug, iter, realDocId)
                .map(d -> ResponseEntity.ok(toResource(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---------- 8. put a source document ----------

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}")
            
    public ResponseEntity<Resource> putSourceDoc(@PathVariable("slug") String slug,
                                                 @PathVariable("iter") String iter,
                                                 @PathVariable("docId") String docId,
                                                 @RequestBody Resource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String realDocId = decodeDocId(docId);
        try {
            DocumentImportService.ImportResult result =
                    importService.importSource(slug, iter, realDocId, body, actor);
            Resource out = toResource(result.document());
            return result.created()
                    ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                    : ResponseEntity.ok(out);
        } catch (IllegalArgumentException | EntityNotFoundException | NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---------- 9. delete a source document ----------

    @DeleteMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}")
    @Transactional
    public ResponseEntity<Void> deleteSourceDoc(@PathVariable("slug") String slug,
                                                @PathVariable("iter") String iter,
                                                @PathVariable("docId") String docId) {
        return deleteSourceDocInternal(slug, iter, decodeDocId(docId));
    }

    private ResponseEntity<Void> deleteSourceDocInternal(String slug, String iter, String docId) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<HDocument> existing = documentRepository.findByVersionAndDocId(slug, iter, docId);
        if (existing.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        HDocument doc = existing.get();
        doc.setObsolete(true);
        documentRepository.save(doc);
        return ResponseEntity.noContent().build();
    }

    // ---------- 9a. CLI no-slash variants: /resource?docId=X ----------
    //
    // SourceDocResourceClient prefers /resource?docId=... over /r/{docId};
    // it falls back to the slash form only on 404. Mirror the legacy
    // contract on the new endpoints.

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/resource")
    @Transactional(readOnly = true)
    public ResponseEntity<Resource> getSourceDocByQuery(@PathVariable("slug") String slug,
                                                        @PathVariable("iter") String iter,
                                                        @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        return getSourceDoc(slug, iter, docId);
    }

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/resource")
    public ResponseEntity<Resource> putSourceDocByQuery(@PathVariable("slug") String slug,
                                                        @PathVariable("iter") String iter,
                                                        @org.springframework.web.bind.annotation.RequestParam("docId") String docId,
                                                        @RequestBody Resource body) {
        return putSourceDoc(slug, iter, docId, body);
    }

    @DeleteMapping("/projects/p/{slug}/iterations/i/{iter}/resource")
    @Transactional
    public ResponseEntity<Void> deleteSourceDocByQuery(@PathVariable("slug") String slug,
                                                       @PathVariable("iter") String iter,
                                                       @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        return deleteSourceDocInternal(slug, iter, decodeDocId(docId));
    }

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/resource/translations/{locale}")
    @Transactional(readOnly = true)
    public ResponseEntity<TranslationsResource> getTranslationsByQuery(@PathVariable("slug") String slug,
                                                                       @PathVariable("iter") String iter,
                                                                       @PathVariable("locale") String locale,
                                                                       @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        return getTranslations(slug, iter, docId, locale);
    }

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/resource/translations/{locale}")
    public ResponseEntity<?> putTranslationsByQuery(@PathVariable("slug") String slug,
                                                    @PathVariable("iter") String iter,
                                                    @PathVariable("locale") String locale,
                                                    @org.springframework.web.bind.annotation.RequestParam("docId") String docId,
                                                    @RequestBody TranslationsResource body) {
        return putTranslations(slug, iter, docId, locale, body);
    }

    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/resource/translations/{locale}")
    public ResponseEntity<ProcessStatus> asyncPushTranslationsByQuery(@PathVariable("slug") String slug,
                                                                      @PathVariable("iter") String iter,
                                                                      @PathVariable("locale") String locale,
                                                                      @org.springframework.web.bind.annotation.RequestParam("docId") String docId,
                                                                      @RequestBody TranslationsResource body) {
        return asyncPushTranslations(slug, iter, docId, locale, body);
    }

    // ---------- 10. get translations ----------

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}/translations/{locale}")
            
    @Transactional(readOnly = true)
    public ResponseEntity<TranslationsResource> getTranslations(@PathVariable("slug") String slug,
                                                                @PathVariable("iter") String iter,
                                                                @PathVariable("docId") String docId,
                                                                @PathVariable("locale") String locale) {
        String realDocId = decodeDocId(docId);
        Optional<HDocument> doc = documentRepository.findByVersionAndDocId(slug, iter, realDocId);
        if (doc.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LocaleId localeId = new LocaleId(locale);
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.get().getId());
        if (flows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Long> ids = new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) ids.add(tf.getId());
        Map<Long, HTextFlowTarget> targets = new HashMap<>();
        for (HTextFlowTarget t : textFlowTargetRepository.findByTextFlowIdsAndLocale(ids, localeId)) {
            targets.put(t.getTextFlow().getId(), t);
        }
        if (targets.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        TranslationsResource out = new TranslationsResource();
        for (HTextFlow tf : flows) {
            HTextFlowTarget t = targets.get(tf.getId());
            if (t == null) continue;
            TextFlowTarget dto = new TextFlowTarget(tf.getResId());
            dto.setState(t.getState() == null ? ContentState.New : t.getState());
            dto.setContents(t.getContents() == null ? List.of("") : t.getContents());
            dto.setRevision(t.getVersionNum());
            dto.setTextFlowRevision(t.getTextFlowRevision());
            // expose who translated so clients can attribute changes (e.g.
            // Co-authored-by trailers in verbaria-lock.json-driven commit messages)
            HPerson author = t.getTranslator() != null ? t.getTranslator()
                    : t.getLastModifiedBy();
            if (author != null) {
                dto.setTranslator(new Person(author.getEmail(), author.getName()));
            }
            out.getTextFlowTargets().add(dto);
        }
        if (out.getTextFlowTargets().isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(out);
    }

    // ---------- 11. put translations ----------

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}/translations/{locale}")
            
    public ResponseEntity<Map<String, Integer>> putTranslations(@PathVariable("slug") String slug,
                                                                @PathVariable("iter") String iter,
                                                                @PathVariable("docId") String docId,
                                                                @PathVariable("locale") String locale,
                                                                @RequestBody TranslationsResource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String realDocId = decodeDocId(docId);
        try {
            DocumentImportService.TranslationsImportResult result =
                    importService.importTranslations(slug, iter, realDocId, locale, body, actor);
            return ResponseEntity.ok(Map.of(
                    "accepted", result.accepted(),
                    "skipped", result.skipped()));
        } catch (IllegalArgumentException | EntityNotFoundException | NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ---------- 12. iteration stats ----------

    @GetMapping("/stats/proj/{slug}/iter/{iter}")
    @Transactional(readOnly = true)
    public ResponseEntity<ContainerTranslationStatistics> iterationStats(
            @PathVariable("slug") String slug,
            @PathVariable("iter") String iter) {
        HProjectIteration iteration =
                iterationRepository.findByProjectAndSlug(slug, iter).orElse(null);
        if (iteration == null) {
            return ResponseEntity.notFound().build();
        }
        long totalSourceWords = iterationRepository.sumSourceWordCount(iteration.getId());
        long totalSourceMsgs = iterationRepository.countSourceTextFlows(iteration.getId());

        // locale -> state -> words
        Map<HLocale, EnumMap<ContentState, Long>> byLocale = new HashMap<>();
        for (Object[] row : textFlowTargetRepository.aggregateWordsByLocaleAndState(iteration.getId())) {
            HLocale loc = (HLocale) row[0];
            ContentState state = (ContentState) row[1];
            Number words = (Number) row[2];
            if (loc == null || state == null || words == null) continue;
            byLocale.computeIfAbsent(loc, k -> new EnumMap<>(ContentState.class))
                    .merge(state, words.longValue(), Long::sum);
        }

        ContainerTranslationStatistics container = new ContainerTranslationStatistics();
        container.setId(slug + ":" + iter);
        // The CLI's ConsoleStatisticsOutput / CsvStatisticsOutput look up a
        // link with rel="statSource" whose type tells them the granularity
        // (PROJ_ITER for project-version stats, DOC for per-document stats).
        // Without these refs the CLI NPEs.
        org.zanata.rest.dto.Links refs = new org.zanata.rest.dto.Links();
        refs.add(new org.zanata.rest.dto.Link(
                java.net.URI.create("/rest/stats/proj/" + slug + "/iter/" + iter),
                "self", "application/vnd.zanata.stats+xml"));
        refs.add(new org.zanata.rest.dto.Link(
                java.net.URI.create("/rest/projects/p/" + slug + "/iterations/i/" + iter),
                "statSource", "PROJ_ITER"));
        container.setRefs(refs);
        List<TranslationStatistics> stats = new ArrayList<>();
        List<HLocale> sortedLocales = new ArrayList<>(byLocale.keySet());
        sortedLocales.sort(Comparator.comparing(
                l -> l.getLocaleId() == null ? "" : l.getLocaleId().getId()));
        for (HLocale loc : sortedLocales) {
            EnumMap<ContentState, Long> counts = byLocale.get(loc);
            long approved = counts.getOrDefault(ContentState.Approved, 0L);
            long translated = counts.getOrDefault(ContentState.Translated, 0L);
            long needReview = counts.getOrDefault(ContentState.NeedReview, 0L);
            long rejected = counts.getOrDefault(ContentState.Rejected, 0L);
            long doneWords = approved + translated + needReview + rejected;
            long untranslatedWords = Math.max(0L, totalSourceWords - doneWords);

            String localeStr = loc.getLocaleId() == null ? "" : loc.getLocaleId().getId();
            TransUnitWords words = new TransUnitWords(
                    (int) approved, (int) needReview, (int) untranslatedWords,
                    (int) translated, (int) rejected);
            TranslationStatistics wordRow = new TranslationStatistics(words, localeStr);
            wordRow.setLastTranslated("");
            stats.add(wordRow);

            // MESSAGE-unit stats: we don't have a per-locale per-state message
            // count aggregate handy, so we approximate by attributing the
            // source message count proportionally to word counts. Good-enough
            // for the CLI's `stats` display until a real message aggregator lands.
            long msgDone = totalSourceWords == 0 ? 0
                    : Math.round(((double) doneWords / totalSourceWords) * totalSourceMsgs);
            long msgUntranslated = Math.max(0L, totalSourceMsgs - msgDone);
            long msgApproved = totalSourceWords == 0 ? 0
                    : Math.round(((double) approved / totalSourceWords) * totalSourceMsgs);
            long msgTranslated = totalSourceWords == 0 ? 0
                    : Math.round(((double) translated / totalSourceWords) * totalSourceMsgs);
            long msgNeedReview = totalSourceWords == 0 ? 0
                    : Math.round(((double) needReview / totalSourceWords) * totalSourceMsgs);
            long msgRejected = totalSourceWords == 0 ? 0
                    : Math.round(((double) rejected / totalSourceWords) * totalSourceMsgs);
            TransUnitCount msgCount = new TransUnitCount(
                    (int) msgApproved, (int) msgNeedReview, (int) msgUntranslated,
                    (int) msgTranslated, (int) msgRejected);
            TranslationStatistics msgRow = new TranslationStatistics(msgCount, localeStr);
            msgRow.setLastTranslated("");
            stats.add(msgRow);
        }
        container.setStats(stats);
        return ResponseEntity.ok(container);
    }

    // ---------- 13. async process status ----------

    @GetMapping("/async/{processId}")
    public ResponseEntity<ProcessStatus> asyncStatus(@PathVariable("processId") String processId) {
        // Push handlers are synchronous in this bridge, so the CLI can stop polling.
        ProcessStatus status = new ProcessStatus();
        status.setStatusCode(ProcessStatus.ProcessStatusCode.Finished);
        status.setPercentageComplete(100);
        status.setUrl(processId);
        return ResponseEntity.ok(status);
    }

    // ---------- 13a. async source-doc push (legacy AsynchronousProcessResource) ----------
    //
    // The CLI's `push --push-type source|both` uses this rather than the sync
    // PUT .../r/{docId}. The legacy contract was: PUT returns a ProcessStatus
    // with an id, the CLI polls /async/{id} until Finished. Since our import
    // service is synchronous we just do the import inline and return Finished.

    // NOT @Transactional on purpose: importService.importSource is itself
    // @Transactional. If this method also opened a transaction, the service
    // call would join it; a failure inside the service marks that shared
    // transaction rollback-only, but our catch(Exception) below swallows the
    // exception and returns a Failed ProcessStatus — then the outer commit
    // throws UnexpectedRollbackException, masking the real error with a raw
    // 500. Letting the service own its transaction means a failure rolls back
    // cleanly and surfaces here with its actual message (matches putSourceDoc).
    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/resource")
    public ResponseEntity<ProcessStatus> asyncPushSource(@PathVariable("slug") String slug,
                                                         @PathVariable("iter") String iter,
                                                         @org.springframework.web.bind.annotation.RequestParam(value = "docId", required = false) String docIdParam,
                                                         @RequestBody Resource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        // Fall back to the Resource name if the docId query param is missing.
        String docId = decodeDocId(docIdParam != null && !docIdParam.isBlank()
                ? docIdParam : body.getName());
        try {
            importService.importSource(slug, iter, docId, body, actor);
        } catch (java.util.NoSuchElementException nse) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            // The CLI discards the response body, so log the real cause here —
            // otherwise an import failure is invisible on both ends.
            log.error("source push failed for {}/{} doc {}", slug, iter, docId, ex);
            ProcessStatus status = new ProcessStatus();
            status.setStatusCode(ProcessStatus.ProcessStatusCode.Failed);
            status.setPercentageComplete(0);
            status.addMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
        ProcessStatus done = new ProcessStatus();
        done.setStatusCode(ProcessStatus.ProcessStatusCode.Finished);
        done.setPercentageComplete(100);
        // CLI's AsyncProcessClient.getProcessStatus appends `url` to
        // /rest/async/ — return a bare opaque id, not a full path.
        done.setUrl("sync-source-push-finished");
        done.addMessage("source push complete (sync)");
        return ResponseEntity.ok(done);
    }

    // ---------- 13b. async translation push ----------

    // NOT @Transactional on purpose — see asyncPushSource: the service method
    // (importTranslations) owns the transaction, so a failure there rolls back
    // cleanly and the real message reaches our catch instead of being masked
    // by an UnexpectedRollbackException at this method's commit.
    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/r/{docId}/translations/{locale}")
    public ResponseEntity<ProcessStatus> asyncPushTranslations(@PathVariable("slug") String slug,
                                                               @PathVariable("iter") String iter,
                                                               @PathVariable("docId") String docId,
                                                               @PathVariable("locale") String locale,
                                                               @RequestBody TranslationsResource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            importService.importTranslations(slug, iter, decodeDocId(docId),
                    locale, body, actor);
        } catch (java.util.NoSuchElementException nse) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            // The CLI discards the response body, so log the real cause here.
            log.error("translation push failed for {}/{} doc {} locale {}",
                    slug, iter, docId, locale, ex);
            ProcessStatus status = new ProcessStatus();
            status.setStatusCode(ProcessStatus.ProcessStatusCode.Failed);
            status.setPercentageComplete(0);
            status.addMessage(ex.getMessage() == null ? ex.toString() : ex.getMessage());
            return ResponseEntity.internalServerError().body(status);
        }
        ProcessStatus done = new ProcessStatus();
        done.setStatusCode(ProcessStatus.ProcessStatusCode.Finished);
        done.setPercentageComplete(100);
        done.setUrl("sync-translations-push-finished");
        done.addMessage("translation push complete (sync)");
        return ResponseEntity.ok(done);
    }

    // ---------- 14. copy trans (intentionally unimplemented) ----------

    @PostMapping("/copytrans/proj/{slug}/iter/{iter}/doc/{docId}")
    public ResponseEntity<ProcessStatus> copyTrans(@PathVariable("slug") String slug,
                                                   @PathVariable("iter") String iter,
                                                   @PathVariable("docId") String docId) {
        ProcessStatus status = new ProcessStatus();
        status.setStatusCode(ProcessStatus.ProcessStatusCode.Failed);
        status.setPercentageComplete(0);
        status.addMessage("copyTrans is not implemented in the Spring bridge yet");
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(status);
    }

    // ---------- 15. download all docs for offline translation ----------
    //
    // GET /rest/file/translation/{project}/{iter}/{locale}/offlinepo
    // returns a ZIP of one file per document, in the project's configured
    // format (.po / .properties / .xlf). Same path the legacy "Download
    // All for Offline Translation" menu item hits.

    @GetMapping("/file/translation/{slug}/{iter}/{locale}/{fileType}")
    public ResponseEntity<byte[]> downloadTranslationBundle(@PathVariable("slug") String slug,
                                                            @PathVariable("iter") String iter,
                                                            @PathVariable("locale") String locale,
                                                            @PathVariable("fileType") String fileType) {
        try {
            org.zanata.spring.service.OfflineExportService.Bundle bundle =
                    offlineExportService.zipForOfflineTranslation(slug, iter, new LocaleId(locale));
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + bundle.filename() + "\"")
                    .header("Content-Type", bundle.contentType())
                    .body(bundle.bytes());
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Per-document translated file download — the "Download translated [format]"
     * action shown on each row of the version's Documents grid.
     */
    @GetMapping("/file/translated-doc/{slug}/{iter}/{locale}")
    public ResponseEntity<byte[]> downloadTranslatedDoc(
            @PathVariable("slug") String slug,
            @PathVariable("iter") String iter,
            @PathVariable("locale") String locale,
            @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        try {
            var bundle = offlineExportService.singleTranslatedDoc(
                    slug, iter, decodeDocId(docId), new LocaleId(locale));
            if (bundle.isEmpty()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + bundle.get().filename() + "\"")
                    .header("Content-Type", bundle.get().contentType())
                    .body(bundle.get().bytes());
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping(value = "/find-doc",
            produces = {org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                    org.springframework.http.MediaType.APPLICATION_XML_VALUE})
    @Transactional(readOnly = true)
    public ResponseEntity<?> findDocByDocId(
            @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        String real = decodeDocId(docId);
        var rows = documentRepository.findByDocIdAcrossProjects(real);
        if (rows.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var d = rows.get(0);
        var iter = d.getProjectIteration();
        return ResponseEntity.ok(java.util.Map.of(
                "docId", d.getDocId(),
                "project", iter.getProject() == null ? null : iter.getProject().getSlug(),
                "version", iter.getSlug()));
    }

    // ---------- 14a. raw-file source upload (YAML / properties / po) ----------
    //
    // Lets curl / scripts push a source file's raw bytes without first
    // converting to a Resource DTO. The server runs SourceUploadService
    // (same code path the Vaadin upload widget uses) so the .yaml,
    // .properties, .po, .pot, .xlf, .xliff branches all just work.

    @org.springframework.web.bind.annotation.PostMapping(
            value = "/file/source/{slug}/{iter}",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSourceFile(
            @PathVariable("slug") String slug,
            @PathVariable("iter") String iter,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "docId", required = false) String docIdOverride) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try (java.io.InputStream in = file.getInputStream()) {
            String fileName = docIdOverride != null && !docIdOverride.isBlank()
                    // If caller forced a docId, append the original extension
                    // so SourceUploadService still dispatches on it.
                    ? docIdOverride + "." + extOf(file.getOriginalFilename())
                    : file.getOriginalFilename();
            DocumentImportService.ImportResult result =
                    sourceUploadService.upload(slug, iter, fileName, in, actor);
            return ResponseEntity.ok(java.util.Map.of(
                    "docId", result.document().getDocId(),
                    "created", result.created(),
                    "revision", result.document().getRevision()));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(java.util.Map.of(
                    "error", iae.getMessage() == null ? iae.toString() : iae.getMessage()));
        } catch (java.io.IOException ioe) {
            return ResponseEntity.internalServerError().body(java.util.Map.of(
                    "error", ioe.getMessage() == null ? ioe.toString() : ioe.getMessage()));
        }
    }

    private static String extOf(String fileName) {
        if (fileName == null) return "yaml";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "yaml" : fileName.substring(dot + 1);
    }

    // ---------- 15a. per-doc YAML download (Consulo localize) ----------
    //
    // GET /rest/file/translation/{slug}/{iter}/{locale}/yaml?docId=foo
    // returns the rendered Consulo-style YAML for one document. Used by
    // the Yaml project-type CLI pull strategy — one file at a time, not
    // a ZIP, so the CLI can lay them out under LOCALIZE-LIB/<locale>/.

    // Distinct URL prefix so Spring's path matcher doesn't compete with
    // the ZIP-bundle endpoint at /file/translation/{slug}/{iter}/{locale}/{fileType}.
    @GetMapping(value = "/yaml/translation/{slug}/{iter}/{locale}",
            produces = {"application/x-yaml", "text/yaml",
                    org.springframework.http.MediaType.TEXT_PLAIN_VALUE,
                    org.springframework.http.MediaType.ALL_VALUE})
    public ResponseEntity<byte[]> downloadYamlForDoc(@PathVariable("slug") String slug,
                                                     @PathVariable("iter") String iter,
                                                     @PathVariable("locale") String locale,
                                                     @org.springframework.web.bind.annotation.RequestParam("docId") String docId) {
        try {
            var bundle = offlineExportService.yamlForDoc(slug, iter,
                    decodeDocId(docId), new LocaleId(locale));
            if (bundle.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .header("Content-Disposition",
                            "attachment; filename=\"" + bundle.get().filename() + "\"")
                    .header("Content-Type", bundle.get().contentType())
                    .body(bundle.get().bytes());
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/tm/projects/{slug}/iterations/{iter}")
    public ResponseEntity<byte[]> exportTmx(@PathVariable("slug") String slug,
                                            @PathVariable("iter") String iter,
                                            @org.springframework.web.bind.annotation.RequestParam("locale") String locale) {
        org.zanata.spring.service.OfflineExportService.Bundle bundle =
                offlineExportService.tmx(slug, iter, new LocaleId(locale));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + bundle.filename() + "\"")
                .header("Content-Type", bundle.contentType())
                .body(bundle.bytes());
    }

    // ---------- helpers ----------

    /**
     * The legacy Zanata REST API encodes slashes in docIds as commas because
     * JAX-RS path segments don't tolerate slashes well.
     */
    private static String decodeDocId(String raw) {
        return raw == null ? null : raw.replace(',', '/');
    }

    private HAccount currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return accountRepository.findByUsername(name).orElse(null);
    }

    private static Project toDto(HProject p) {
        Project dto = new Project();
        dto.setId(p.getSlug());
        dto.setName(p.getName() == null ? p.getSlug() : p.getName());
        dto.setDescription(p.getDescription());
        dto.setSourceViewURL(p.getSourceViewURL());
        dto.setSourceCheckoutURL(p.getSourceCheckoutURL());
        dto.setStatus(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
        dto.setDefaultType(p.getDefaultProjectType() == null
                ? ProjectType.File.toString()
                : p.getDefaultProjectType().toString());
        List<ProjectIteration> iters = new ArrayList<>();
        if (p.getProjectIterations() != null) {
            for (HProjectIteration i : p.getProjectIterations()) {
                iters.add(toDto(i));
            }
        }
        dto.setIterations(iters);
        return dto;
    }

    private static ProjectIteration toDto(HProjectIteration i) {
        ProjectIteration dto = new ProjectIteration(i.getSlug());
        dto.setStatus(i.getStatus() == null ? EntityStatus.ACTIVE : i.getStatus());
        ProjectType pt = i.getProjectType();
        if (pt != null) {
            dto.setProjectType(pt.toString());
        }
        return dto;
    }

    private static Resource toResource(HDocument d) {
        Resource r = new Resource(d.getDocId());
        r.setContentType(d.getContentType() == null
                ? ContentType.TextPlain : d.getContentType());
        r.setLang(d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? LocaleId.EN_US : d.getLocale().getLocaleId());
        r.setType(ResourceType.FILE);
        r.setRevision(d.getRevision());
        for (HTextFlow tf : d.getTextFlows()) {
            if (tf.isObsolete()) continue;
            TextFlow flowDto = new TextFlow(
                    tf.getResId(),
                    d.getLocale() == null || d.getLocale().getLocaleId() == null
                            ? LocaleId.EN_US : d.getLocale().getLocaleId(),
                    tf.getContents() == null ? List.<String>of("") : tf.getContents());
            flowDto.setPlural(tf.isPlural());
            flowDto.setRevision(tf.getRevision());
            HPotEntryData ped = tf.getPotEntryData();
            if (ped != null && ped.getContext() != null
                    && !ped.getContext().isEmpty()) {
                PotEntryHeader peh = new PotEntryHeader();
                peh.setContext(ped.getContext());
                flowDto.getExtensions(true).add(peh);
            }
            // Consulo raw sub-file: hand back its extension so source pull
            // recreates the exact file and the editor can show/change it.
            // Present (even empty) means the entry is a raw sub-file.
            if (tf.getConsuloFileExt() != null) {
                flowDto.getExtensions(true).add(
                        new ConsuloSubFile(tf.getConsuloFileExt()));
            }
            r.getTextFlows().add(flowDto);
        }
        return r;
    }
}
