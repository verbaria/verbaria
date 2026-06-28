package org.verbaria.server.headless.web.rest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.verbaria.server.headless.service.*;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.rest.dto.LocaleDetails;
import org.zanata.rest.dto.PushStartResponse;
import org.zanata.rest.dto.PushStatus;
import org.zanata.rest.dto.VersionInfo;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;

@RestController
@RequestMapping("/rest")
public class ZanataCliBridgeController {

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final LocaleRepository localeRepository;
    private final AccountRepository accountRepository;
    private final PushPlanService pushPlanService;
    private final PushSessionService pushSessionService;
    private final PullArchiveService pullArchiveService;
    private final DocumentLayoutRegistry layoutRegistry;

    @Value("${spring.application.version:unknown}")
    private String applicationVersion;

    public ZanataCliBridgeController(ProjectRepository projectRepository,
                                     ProjectIterationRepository iterationRepository,
                                     LocaleRepository localeRepository,
                                     AccountRepository accountRepository,
                                     PushPlanService pushPlanService,
                                     PushSessionService pushSessionService,
                                     PullArchiveService pullArchiveService,
                                     DocumentLayoutRegistry layoutRegistry) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.localeRepository = localeRepository;
        this.accountRepository = accountRepository;
        this.pushPlanService = pushPlanService;
        this.pushSessionService = pushSessionService;
        this.pullArchiveService = pullArchiveService;
        this.layoutRegistry = layoutRegistry;
    }

    @GetMapping("/version")
    public ResponseEntity<VersionInfo> version() {
        return ResponseEntity.ok(new VersionInfo(
                applicationVersion,
                Instant.now().toString(),
                "spring"));
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
        String pt = iteration.get().getEffectiveProjectType();
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", resolveBaseUrl(host, forwardedHost, forwardedProto));
        config.put("project", slug);
        config.put("projectVersion", iter);
        if (pt != null) {
            config.put("projectType", pt.toLowerCase());
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
        Iterable<HLocale> source = customized.isPresent()
                ? customized.get() : localeRepository.findAll();
        var projectSource = iterationRepository.findProjectSourceLocale(iterOpt.get().getId());
        LinkedHashMap<Long, HLocale> uniq = new LinkedHashMap<>();
        for (var l : source) if (l.isActive()) uniq.put(l.getId(), l);
        projectSource.filter(HLocale::isActive)
                .ifPresent(l -> uniq.putIfAbsent(l.getId(), l));
        List<LocaleDetails> details = new ArrayList<>();
        for (var loc : uniq.values()) {
            if (loc.getLocaleId() == null) continue;
            LocaleDetails ld = new LocaleDetails(
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
        String type;
        try {
            type = resolveType(body.projectType(), body.project());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            @RequestParam(value = "projectType", required = false) String projectType,
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
        String type;
        try {
            type = resolveType(projectType, project);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        try (InputStream in = archive.getInputStream()) {
            String sessionId = pushSessionService.start(type, project, version,
                    targetLocales, sourceLang, force, in.readAllBytes(),
                    actor.getUsername());
            return ResponseEntity.accepted().body(new PushStartResponse(sessionId));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", iae.getMessage() == null ? iae.toString() : iae.getMessage()));
        } catch (IOException ioe) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", ioe.getMessage() == null ? ioe.toString() : ioe.getMessage()));
        }
    }

    @GetMapping("/push-status/{sessionId}")
    public ResponseEntity<?> pushStatus(@PathVariable("sessionId") String sessionId) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PushStatus status = pushSessionService.status(sessionId);
        if (status == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }

    @GetMapping(value = "/pull-archive")
    public ResponseEntity<?> pullArchive(
            @RequestParam(value = "projectType", required = false) String projectType,
            @RequestParam("project") String project,
            @RequestParam("version") String version,
            @RequestParam(value = "pullType", required = false) String pullType,
            @RequestParam(value = "targetLocales", required = false) List<String> targetLocales) {
        if (currentUser() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String type;
        try {
            type = resolveType(projectType, project);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

    private String resolveType(String projectType, String pattern) {
        if (projectType != null && !projectType.isBlank()) {
            if (!layoutRegistry.isKnown(projectType)) {
                throw new IllegalArgumentException(
                        "Unknown project type: " + projectType);
            }
            return projectType;
        }
        HProject p;
        if (pattern != null && pattern.indexOf('*') >= 0) {
            String regex = pattern.replace("**", "*")
                    .replace(".", "\\.").replace("*", ".*");
            p = projectRepository.findAllByOrderBySlugAsc().stream()
                    .filter(x -> x.getSlug().matches(regex))
                    .findFirst().orElse(null);
        } else {
            p = projectRepository.findBySlug(pattern).orElse(null);
        }
        if (p != null && p.getDefaultProjectType() != null) {
            return p.getDefaultProjectType();
        }
        throw new IllegalArgumentException(
                "Project type not configured on project: " + pattern);
    }

    private HAccount currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return accountRepository.findByUsername(name).orElse(null);
    }
}
