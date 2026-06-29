package org.verbaria.server.headless.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectRepository;

@Service
public class LockService {

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final LocaleRepository localeRepository;
    private final OfflineExportService exportService;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public LockService(ProjectRepository projectRepository,
            DocumentRepository documentRepository,
            LocaleRepository localeRepository,
            OfflineExportService exportService) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.localeRepository = localeRepository;
        this.exportService = exportService;
    }

    @Transactional(readOnly = true)
    public byte[] buildLockJson(String pattern, String version,
            List<String> requestedLocales, boolean includeSourceTarget)
            throws IOException {
        return mapper.writeValueAsBytes(buildLock(pattern, version,
                requestedLocales, includeSourceTarget));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> buildLock(String pattern, String version,
            List<String> requestedLocales, boolean includeSourceTarget) {
        boolean glob = pattern.indexOf('*') >= 0;
        List<HProject> projects = matchingProjects(pattern, glob);
        Map<String, Object> documents = new TreeMap<>();
        String sourceLocale = "en";
        for (HProject project : projects) {
            HLocale projectSource = project.getEffectiveDefaultSourceLocale();
            for (HDocument d : documentRepository
                    .findByVersion(project.getSlug(), version)) {
                String docSourceId = d.getLocale() != null
                        ? d.getLocale().getLocaleId().getId()
                        : projectSource != null
                                ? projectSource.getLocaleId().getId() : "en";
                sourceLocale = docSourceId;
                String key = glob ? project.getSlug() + "/" + d.getDocId()
                        : d.getDocId();
                Map<String, Object> docLock = documentLock(d, docSourceId,
                        requestedLocales, project, includeSourceTarget);
                // Only track documents this repo actually translates: a doc with
                // no target-locale translation (pure source) is left out, so the
                // lock isn't bloated with untranslated documents.
                if (hasTargetTranslation(docLock, docSourceId)) {
                    documents.put(key, docLock);
                }
            }
        }
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("lockVersion", 1);
        lock.put("project", pattern);
        lock.put("projectVersion", version);
        lock.put("sourceLocale", sourceLocale);
        lock.put("generatedAt", Instant.now().toString());
        lock.put("documents", documents);
        return lock;
    }

    private static boolean hasTargetTranslation(Map<String, Object> docLock,
            String sourceLocale) {
        Object t = docLock.get("translations");
        if (!(t instanceof Map<?, ?> m)) {
            return false;
        }
        for (Object localeId : m.keySet()) {
            if (!sameLocale(String.valueOf(localeId), sourceLocale)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> documentLock(HDocument d, String docSourceId,
            List<String> requestedLocales, HProject project,
            boolean includeSourceTarget) {
        Resource source = exportService.toResource(d);
        Map<String, Object> docLock = new LinkedHashMap<>();
        Map<String, Object> src = new LinkedHashMap<>();
        // The signature is all the changelog needs to detect a source change;
        // the revision is redundant.
        src.put("sig", LockBuilder.sourceSignature(source));
        docLock.put("source", src);
        Map<String, Object> transLocks = new TreeMap<>();
        for (HLocale loc : lockLocales(docSourceId, requestedLocales, project,
                includeSourceTarget)) {
            TranslationsResource trans =
                    exportService.toTranslations(d, loc.getLocaleId());
            Map<String, Object> tl = LockBuilder.translationLock(trans, false);
            if (tl != null) {
                transLocks.put(loc.getLocaleId().getId(), tl);
            }
        }
        if (!transLocks.isEmpty()) {
            docLock.put("translations", transLocks);
        }
        return docLock;
    }

    private List<HLocale> lockLocales(String docSourceId,
            List<String> requestedLocales, HProject project,
            boolean includeSourceTarget) {
        List<HLocale> out = new ArrayList<>();
        if (includeSourceTarget) {
            localeRepository.findByLocaleId(new LocaleId(docSourceId))
                    .ifPresent(out::add);
        }
        List<HLocale> targets;
        if (requestedLocales != null && !requestedLocales.isEmpty()) {
            targets = new ArrayList<>();
            for (String id : requestedLocales) {
                localeRepository.findByLocaleId(new LocaleId(id))
                        .ifPresent(targets::add);
            }
        } else {
            var customized = project.getEffectiveCustomizedLocales();
            targets = customized == null ? List.of() : List.copyOf(customized);
        }
        for (HLocale t : targets) {
            if (!sameLocale(t.getLocaleId().getId(), docSourceId)) {
                out.add(t);
            }
        }
        return out;
    }

    private List<HProject> matchingProjects(String pattern, boolean glob) {
        if (!glob) {
            return projectRepository.findBySlug(pattern)
                    .map(List::of).orElse(List.of());
        }
        List<HProject> out = new ArrayList<>();
        for (HProject p : projectRepository.findAllByOrderBySlugAsc()) {
            if (matchesPattern(p.getSlug(), pattern)) {
                out.add(p);
            }
        }
        return out;
    }

    static boolean matchesPattern(String slug, String pattern) {
        String regex = pattern.replace("**", "*")
                .replace(".", "\\.").replace("*", ".*");
        return slug.matches(regex);
    }

    private static boolean sameLocale(String a, String b) {
        return a != null && b != null
                && a.replace('-', '_').equalsIgnoreCase(b.replace('-', '_'));
    }
}
