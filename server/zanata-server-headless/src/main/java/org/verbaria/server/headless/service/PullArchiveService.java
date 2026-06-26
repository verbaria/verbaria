package org.verbaria.server.headless.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.rest.dto.resource.Resource;
import org.zanata.rest.dto.resource.TranslationsResource;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectRepository;

@Service
public class PullArchiveService {

    private final DocumentLayoutRegistry layouts;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final LocaleRepository localeRepository;
    private final OfflineExportService exportService;
    private final LockService lockService;

    public PullArchiveService(DocumentLayoutRegistry layouts,
            ProjectRepository projectRepository,
            DocumentRepository documentRepository,
            LocaleRepository localeRepository,
            OfflineExportService exportService,
            LockService lockService) {
        this.layouts = layouts;
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.localeRepository = localeRepository;
        this.exportService = exportService;
        this.lockService = lockService;
    }

    @Transactional(readOnly = true)
    public byte[] pull(ProjectType type, String projectSlug, String version,
            String pullType, List<String> requestedLocales) throws IOException {
        DocumentLayout layout = layouts.forType(type).orElseThrow(
                () -> new IllegalArgumentException(
                        "No document layout for project type: " + type));
        boolean wantSource = !"trans".equalsIgnoreCase(pullType);
        boolean wantTrans = !"source".equalsIgnoreCase(pullType);

        List<HProject> projects = matchingProjects(projectSlug);
        boolean glob = projects.size() > 1 || projectSlug.indexOf('*') >= 0;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            for (HProject project : projects) {
                String prefix = glob ? project.getSlug() + "/" : "";
                List<HLocale> targets = wantTrans
                        ? targetLocales(project, requestedLocales) : List.of();
                String projectSource = project.getEffectiveDefaultSourceLocale() == null
                        ? "en"
                        : project.getEffectiveDefaultSourceLocale().getLocaleId().getId();
                for (HDocument d : documentRepository
                        .findByVersion(project.getSlug(), version)) {
                    Resource source = exportService.toResource(d);
                    String docSourceId = d.getLocale() == null
                            ? projectSource : d.getLocale().getLocaleId().getId();
                    if (wantSource) {
                        add(zip, prefix + layout.sourceOutputPath(d.getDocId(), docSourceId),
                                layout.writeSource(source));
                    }
                    if (wantTrans) {
                        for (HLocale loc : targets) {
                            String localeId = loc.getLocaleId().getId();
                            if (sameLocale(localeId, docSourceId)) {
                                continue;
                            }
                            TranslationsResource trans = exportService
                                    .toTranslations(d, loc.getLocaleId());
                            add(zip, prefix + layout.outputPath(d.getDocId(), localeId),
                                    layout.writeTranslation(source, trans, localeId));
                        }
                    }
                }
            }
            add(zip, "verbaria-lock.json", lockService.buildLockJson(
                    projectSlug, version, requestedLocales, true));
        }
        return buf.toByteArray();
    }

    private List<HProject> matchingProjects(String pattern) {
        if (pattern.indexOf('*') < 0) {
            return projectRepository.findBySlug(pattern).map(List::of)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown project: " + pattern));
        }
        List<HProject> out = new ArrayList<>();
        for (HProject p : projectRepository.findAllByOrderBySlugAsc()) {
            if (LockService.matchesPattern(p.getSlug(), pattern)) {
                out.add(p);
            }
        }
        return out;
    }

    private List<HLocale> targetLocales(HProject project,
            List<String> requestedLocales) {
        if (requestedLocales != null && !requestedLocales.isEmpty()) {
            List<HLocale> out = new ArrayList<>();
            for (String id : requestedLocales) {
                localeRepository.findByLocaleId(new LocaleId(id))
                        .ifPresent(out::add);
            }
            return out;
        }
        Set<HLocale> customized = project.getEffectiveCustomizedLocales();
        if (customized != null && !customized.isEmpty()) {
            return List.copyOf(customized);
        }
        return localeRepository.findAll().stream()
                .filter(HLocale::isActive).toList();
    }

    private static boolean sameLocale(String a, String b) {
        return a != null && b != null
                && a.replace('-', '_').equalsIgnoreCase(b.replace('-', '_'));
    }

    private static void add(ZipOutputStream zip, String path, byte[] bytes)
            throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
    }
}
