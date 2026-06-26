package org.verbaria.server.headless.web.rest;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
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
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.rest.dto.Person;
import org.zanata.rest.dto.ProcessStatus;
import org.zanata.rest.dto.Project;
import org.zanata.rest.dto.ProjectIteration;
import org.zanata.rest.dto.VersionInfo;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.ResourceMeta;
import org.zanata.rest.dto.resource.TextFlow;
import org.zanata.rest.dto.resource.TextFlowTarget;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.zanata.rest.dto.stats.ContainerTranslationStatistics;
import org.zanata.rest.dto.stats.TranslationStatistics;
import org.verbaria.server.headless.extension.TextFlowExtensionStore;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.verbaria.server.headless.service.DocumentImportService;
import org.verbaria.server.headless.service.LockService;
import org.verbaria.server.headless.service.OfflineExportService;
import org.verbaria.server.headless.service.PullArchiveService;
import org.verbaria.server.headless.service.PushArchiveService;
import org.verbaria.server.headless.service.PushPlanService;
import org.verbaria.server.headless.service.ProjectHierarchyService;
import org.verbaria.server.headless.service.SourceUploadService;
import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/rest")
public class ZanataCliBridgeController {

    private static final Logger log =
            LoggerFactory.getLogger(ZanataCliBridgeController.class);

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final DocumentImportService importService;
    private final OfflineExportService offlineExportService;
    private final SourceUploadService sourceUploadService;
    private final TextFlowExtensionStore extensionStore;
    private final ProjectHierarchyService hierarchyService;
    private final PushPlanService pushPlanService;
    private final PushArchiveService pushArchiveService;
    private final PullArchiveService pullArchiveService;
    private final LockService lockService;

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
                                     OfflineExportService offlineExportService,
                                     SourceUploadService sourceUploadService,
                                     TextFlowExtensionStore extensionStore,
                                     ProjectHierarchyService hierarchyService,
                                     PushPlanService pushPlanService,
                                     PushArchiveService pushArchiveService,
                                     PullArchiveService pullArchiveService,
                                     LockService lockService) {
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
        this.extensionStore = extensionStore;
        this.hierarchyService = hierarchyService;
        this.pushPlanService = pushPlanService;
        this.pushArchiveService = pushArchiveService;
        this.pullArchiveService = pullArchiveService;
        this.lockService = lockService;
    }

    @GetMapping("/version")
    public ResponseEntity<VersionInfo> version() {
        return ResponseEntity.ok(new VersionInfo(
                applicationVersion,
                Instant.now().toString(),
                "spring"));
    }

    @GetMapping(value = "/projects",
            produces = {MediaType.APPLICATION_JSON_VALUE,
                        "application/vnd.zanata.projects+json"})
    @Transactional(readOnly = true)
    public ResponseEntity<?> listProjects() {
        List<Project> dtos = new ArrayList<>();
        for (HProject p : projectRepository.findAll()) {
            if (p.getStatus() == EntityStatus.OBSOLETE) continue;
            Project dto = new Project();
            dto.setId(p.getSlug());
            dto.setName(p.getName() == null ? p.getSlug() : p.getName());
            dto.setDescription(p.getDescription());
            dto.setStatus(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
            dto.setDefaultType(p.getDefaultProjectType() == null
                    ? ProjectType.File.toString()
                    : p.getDefaultProjectType().toString());
            dtos.add(dto);
        }
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/projects/p/{slug}/cli")
    @Transactional(readOnly = true)
    public ResponseEntity<Project> getProjectCli(@PathVariable("slug") String slug) {
        return projectRepository.findBySlugWithIterations(slug)
                .map(p -> ResponseEntity.ok(toDto(p)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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
        if (body.getStatus() != null) p.setStatus(body.getStatus());
        if (body.getDefaultType() != null) {
            try {
                p.setDefaultProjectType(ProjectType.getValueOf(body.getDefaultType()));
            } catch (Exception ignore) {
            }
        }
        if (p.getName() == null || p.getName().isEmpty()) {
            p.setName(slug);
        }
        HProject saved = projectRepository.save(p);
        Project out = toDto(saved);
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                : ResponseEntity.ok(out);
    }

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectIteration> getIteration(@PathVariable("slug") String slug,
                                                         @PathVariable("iter") String iter) {
        return iterationRepository.findByProjectAndSlug(slug, iter)
                .map(i -> ResponseEntity.ok(toDto(i)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

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
        ProjectType pt = iteration.get().getEffectiveProjectType();
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

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/locales")
    @Transactional(readOnly = true)
    public ResponseEntity<?> iterationLocales(@PathVariable("slug") String slug,
                                              @PathVariable("iter") String iter) {
        var iterOpt = iterationRepository.findByProjectAndSlug(slug, iter);
        if (iterOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var customized = iterationRepository.findProjectLocales(iterOpt.get().getId());
        Iterable<org.zanata.model.HLocale> source = customized.isPresent()
                ? customized.get() : localeRepository.findAll();
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
                    null ,
                    loc.getNativeName(),
                    true ,
                    true ,
                    loc.getPluralForms(),
                    false );
            details.add(ld);
        }
        return ResponseEntity.ok(details);
    }

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
            }
        }
        HProjectIteration saved = iterationRepository.save(i);
        if (created) {
            hierarchyService.propagateVersionToChildren(project, iter);
        }
        ProjectIteration out = toDto(saved);
        return created
                ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                : ResponseEntity.ok(out);
    }

    @GetMapping("/projects/p/{slug}/iterations/i/{iter}/r")
    @Transactional(readOnly = true)
    public ResponseEntity<?> listSourceDocs(@PathVariable("slug") String slug,
                                            @PathVariable("iter") String iter) {
        if (iterationRepository.findByProjectAndSlug(slug, iter).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
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

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}")

    public ResponseEntity<Resource> putSourceDoc(@PathVariable("slug") String slug,
                                                 @PathVariable("iter") String iter,
                                                 @PathVariable("docId") String docId,
                                                 @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                 @RequestBody Resource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String realDocId = decodeDocId(docId);
        try {
            DocumentImportService.ImportResult result =
                    importService.importSource(slug, iter, realDocId, body, actor, force);
            Resource out = toResource(result.document());
            return result.created()
                    ? ResponseEntity.status(HttpStatus.CREATED).body(out)
                    : ResponseEntity.ok(out);
        } catch (IllegalArgumentException | EntityNotFoundException | NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

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
                                                        @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                        @RequestBody Resource body) {
        return putSourceDoc(slug, iter, docId, force, body);
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
                                                    @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                    @RequestBody TranslationsResource body) {
        return putTranslations(slug, iter, docId, locale, force, body);
    }

    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/resource/translations/{locale}")
    public ResponseEntity<ProcessStatus> asyncPushTranslationsByQuery(@PathVariable("slug") String slug,
                                                                      @PathVariable("iter") String iter,
                                                                      @PathVariable("locale") String locale,
                                                                      @org.springframework.web.bind.annotation.RequestParam("docId") String docId,
                                                                      @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                                      @RequestBody TranslationsResource body) {
        return asyncPushTranslations(slug, iter, docId, locale, force, body);
    }

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
            HTextFlowTargetHistory good = null;
            if (t.getState() == ContentState.Rejected) {
                good = OfflineExportService.lastGoodVersion(t);
                if (good == null) continue;
            }
            List<String> contents = good != null ? good.getContents() : t.getContents();
            ContentState state = good != null ? good.getState()
                    : (t.getState() == null ? ContentState.New : t.getState());
            TextFlowTarget dto = new TextFlowTarget(tf.getResId());
            dto.setState(state);
            dto.setContents(contents == null ? List.of("") : contents);
            dto.setRevision(t.getVersionNum());
            dto.setTextFlowRevision(t.getTextFlowRevision());
            HPerson author = good != null
                    ? (good.getTranslator() != null ? good.getTranslator() : good.getLastModifiedBy())
                    : (t.getTranslator() != null ? t.getTranslator() : t.getLastModifiedBy());
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

    @PutMapping("/projects/p/{slug}/iterations/i/{iter}/r/{docId}/translations/{locale}")

    public ResponseEntity<Map<String, Integer>> putTranslations(@PathVariable("slug") String slug,
                                                                @PathVariable("iter") String iter,
                                                                @PathVariable("docId") String docId,
                                                                @PathVariable("locale") String locale,
                                                                @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                                @RequestBody TranslationsResource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String realDocId = decodeDocId(docId);
        try {
            DocumentImportService.TranslationsImportResult result =
                    importService.importTranslations(slug, iter, realDocId, locale, body, actor, force);
            return ResponseEntity.ok(Map.of(
                    "accepted", result.accepted(),
                    "skipped", result.skipped(),
                    "unchanged", result.unchanged()));
        } catch (IllegalArgumentException | EntityNotFoundException | NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }

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

    @GetMapping("/async/{processId}")
    public ResponseEntity<ProcessStatus> asyncStatus(@PathVariable("processId") String processId) {
        ProcessStatus status = new ProcessStatus();
        status.setStatusCode(ProcessStatus.ProcessStatusCode.Finished);
        status.setPercentageComplete(100);
        status.setUrl(processId);
        return ResponseEntity.ok(status);
    }

    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/resource")
    public ResponseEntity<ProcessStatus> asyncPushSource(@PathVariable("slug") String slug,
                                                         @PathVariable("iter") String iter,
                                                         @org.springframework.web.bind.annotation.RequestParam(value = "docId", required = false) String docIdParam,
                                                         @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                         @RequestBody Resource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String docId = decodeDocId(docIdParam != null && !docIdParam.isBlank()
                ? docIdParam : body.getName());
        try {
            importService.importSource(slug, iter, docId, body, actor, force);
        } catch (java.util.NoSuchElementException nse) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
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
        done.setUrl("sync-source-push-finished");
        done.addMessage("source push complete (sync)");
        return ResponseEntity.ok(done);
    }

    @PutMapping("/async/projects/p/{slug}/iterations/i/{iter}/r/{docId}/translations/{locale}")
    public ResponseEntity<ProcessStatus> asyncPushTranslations(@PathVariable("slug") String slug,
                                                               @PathVariable("iter") String iter,
                                                               @PathVariable("docId") String docId,
                                                               @PathVariable("locale") String locale,
                                                               @org.springframework.web.bind.annotation.RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
                                                               @RequestBody TranslationsResource body) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            importService.importTranslations(slug, iter, decodeDocId(docId),
                    locale, body, actor, force);
        } catch (java.util.NoSuchElementException nse) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
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

    @GetMapping("/file/translation/{slug}/{iter}/{locale}/{fileType}")
    public ResponseEntity<byte[]> downloadTranslationBundle(@PathVariable("slug") String slug,
                                                            @PathVariable("iter") String iter,
                                                            @PathVariable("locale") String locale,
                                                            @PathVariable("fileType") String fileType) {
        try {
            OfflineExportService.Bundle bundle =
                    offlineExportService.zipForOfflineTranslation(slug, iter, new LocaleId(locale));
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + bundle.filename() + "\"")
                    .header("Content-Type", bundle.contentType())
                    .body(bundle.bytes());
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

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

    public record PushPlanRequest(String projectType, String project,
            List<String> paths, List<String> targetLocales, String sourceLang) {
    }

    @PostMapping(value = "/push-plan",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pushPlan(@RequestBody PushPlanRequest body) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProjectType type;
        try {
            type = ProjectType.getValueOf(body.projectType());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown project type: " + body.projectType()));
        }
        List<String> paths = body.paths() == null ? List.of() : body.paths();
        try {
            return ResponseEntity.ok(Map.of("entries", pushPlanService.plan(
                    type, body.project(), paths, body.targetLocales(),
                    body.sourceLang())));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", iae.getMessage()));
        }
    }

    @PostMapping(value = "/push-archive",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> pushArchive(
            @RequestParam("projectType") String projectType,
            @RequestParam("project") String project,
            @RequestParam("version") String version,
            @RequestParam(value = "targetLocales", required = false) List<String> targetLocales,
            @RequestParam(value = "sourceLang", required = false) String sourceLang,
            @RequestParam(value = "force", required = false, defaultValue = "false") boolean force,
            @RequestParam("archive") MultipartFile archive) {
        HAccount actor = currentUser();
        if (actor == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProjectType type;
        try {
            type = ProjectType.getValueOf(projectType);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown project type: " + projectType));
        }
        try (InputStream in = archive.getInputStream()) {
            var imported = pushArchiveService.push(
                    type, project, version, targetLocales, sourceLang, force, in, actor);
            return ResponseEntity.ok(Map.of("imported", imported,
                    "lock", lockService.buildLock(project, version, targetLocales, false)));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", iae.getMessage() == null ? iae.toString() : iae.getMessage()));
        } catch (IOException ioe) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", ioe.getMessage() == null ? ioe.toString() : ioe.getMessage()));
        }
    }

    @GetMapping(value = "/pull-archive")
    public ResponseEntity<?> pullArchive(
            @RequestParam("projectType") String projectType,
            @RequestParam("project") String project,
            @RequestParam("version") String version,
            @RequestParam(value = "pullType", required = false) String pullType,
            @RequestParam(value = "targetLocales", required = false) List<String> targetLocales) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProjectType type;
        try {
            type = ProjectType.getValueOf(projectType);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown project type: " + projectType));
        }
        try {
            byte[] zip = pullArchiveService.pull(type, project, version,
                    pullType, targetLocales);
            return ResponseEntity.ok()
                    .header("Content-Type", "application/zip")
                    .header("Content-Disposition",
                            "attachment; filename=\"" + project + "-" + version + ".zip\"")
                    .body(zip);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", iae.getMessage() == null ? iae.toString() : iae.getMessage()));
        } catch (IOException ioe) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", ioe.getMessage() == null ? ioe.toString() : ioe.getMessage()));
        }
    }

    @GetMapping(value = "/find-doc",
            produces = {MediaType.APPLICATION_JSON_VALUE,
                    MediaType.APPLICATION_XML_VALUE})
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
                "project", iter.getProject() == null ? "" : iter.getProject().getSlug(),
                "version", iter.getSlug()));
    }

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
        try (InputStream in = file.getInputStream()) {
            String fileName = docIdOverride != null && !docIdOverride.isBlank()
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
        OfflineExportService.Bundle bundle =
                offlineExportService.tmx(slug, iter, new LocaleId(locale));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + bundle.filename() + "\"")
                .header("Content-Type", bundle.contentType())
                .body(bundle.bytes());
    }

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
        dto.setSourceViewURL(p.getResolvedSourceViewURL());
        dto.setStatus(p.getStatus() == null ? EntityStatus.ACTIVE : p.getStatus());
        ProjectType effectiveType = p.getEffectiveDefaultProjectType();
        dto.setDefaultType(effectiveType == null
                ? ProjectType.File.toString()
                : effectiveType.toString());
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

    private Resource toResource(HDocument d) {
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
            extensionStore.emit(tf, flowDto);
            r.getTextFlows().add(flowDto);
        }
        return r;
    }
}
