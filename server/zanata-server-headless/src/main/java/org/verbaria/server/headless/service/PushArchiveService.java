package org.verbaria.server.headless.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.common.LocaleId;
import org.zanata.common.ProjectType;
import org.zanata.model.HAccount;
import org.zanata.rest.dto.resource.Resource;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;

@Service
public class PushArchiveService {

    private final DocumentLayoutRegistry layouts;
    private final PushPlanService pushPlanService;
    private final DocumentImportService importService;

    public PushArchiveService(DocumentLayoutRegistry layouts,
            PushPlanService pushPlanService,
            DocumentImportService importService) {
        this.layouts = layouts;
        this.pushPlanService = pushPlanService;
        this.importService = importService;
    }

    public record Imported(String path, String docId, String localeId,
            String project, boolean source) {
    }

    public List<Imported> push(ProjectType type, String pattern, String version,
            List<String> targetLocales, String sourceLang, boolean force,
            InputStream archive, HAccount actor) throws IOException {
        Map<String, byte[]> files = unzip(archive);
        DocumentLayout layout = layouts.forType(type).orElseThrow(
                () -> new IllegalArgumentException(
                        "No document layout for project type: " + type));
        List<PushPlanService.PlanEntry> plan = pushPlanService.plan(type, pattern,
                new ArrayList<>(files.keySet()), targetLocales, sourceLang);
        List<Imported> done = new ArrayList<>();
        for (PushPlanService.PlanEntry e : plan) {
            if (e.source()) {
                importOne(layout, version, e, files.get(e.path()), force, actor, done);
            }
        }
        for (PushPlanService.PlanEntry e : plan) {
            if (!e.source()) {
                importOne(layout, version, e, files.get(e.path()), force, actor, done);
            }
        }
        return done;
    }

    private void importOne(DocumentLayout layout, String version,
            PushPlanService.PlanEntry e, byte[] bytes, boolean force,
            HAccount actor, List<Imported> done) throws IOException {
        if (bytes == null) {
            return;
        }
        if (e.source()) {
            Resource resource = layout.readSource(e.docId(), bytes);
            if (e.localeId() != null && !e.localeId().isEmpty()) {
                resource.setLang(new LocaleId(e.localeId()));
            }
            importService.importSource(e.project(), version, e.docId(),
                    resource, actor, force);
        } else {
            importService.importTranslations(e.project(), version, e.docId(),
                    e.localeId(), layout.readTranslation(bytes), actor, force);
        }
        done.add(new Imported(e.path(), e.docId(), e.localeId(), e.project(),
                e.source()));
    }

    private static Map<String, byte[]> unzip(InputStream in) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                files.put(entry.getName().replace('\\', '/'), out.toByteArray());
            }
        }
        return files;
    }
}
