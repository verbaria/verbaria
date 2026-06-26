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
        Map<String, List<PushPlanService.PlanEntry>> groups =
                new LinkedHashMap<>();
        for (PushPlanService.PlanEntry e : plan) {
            String key = e.source() + "|" + e.project() + "|" + e.docId()
                    + "|" + e.localeId();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
        }
        List<Imported> done = new ArrayList<>();
        for (List<PushPlanService.PlanEntry> group : groups.values()) {
            if (group.get(0).source()) {
                importSourceGroup(layout, version, group, files, force, actor,
                        done);
            }
        }
        for (List<PushPlanService.PlanEntry> group : groups.values()) {
            if (!group.get(0).source()) {
                importTransGroup(layout, version, group, files, force, actor,
                        done);
            }
        }
        return done;
    }

    private void importSourceGroup(DocumentLayout layout, String version,
            List<PushPlanService.PlanEntry> group, Map<String, byte[]> files,
            boolean force, HAccount actor, List<Imported> done)
            throws IOException {
        PushPlanService.PlanEntry first = group.get(0);
        Map<String, byte[]> groupFiles = new LinkedHashMap<>();
        for (PushPlanService.PlanEntry e : group) {
            byte[] bytes = files.get(e.path());
            if (bytes != null) {
                groupFiles.put(e.path(), bytes);
            }
        }
        if (groupFiles.isEmpty()) {
            return;
        }
        Resource resource = layout.readSource(first.docId(), groupFiles);
        if (first.localeId() != null && !first.localeId().isEmpty()) {
            resource.setLang(new LocaleId(first.localeId()));
        }
        importService.importSource(first.project(), version, first.docId(),
                resource, actor, force);
        for (PushPlanService.PlanEntry e : group) {
            if (files.get(e.path()) != null) {
                done.add(new Imported(e.path(), e.docId(), e.localeId(),
                        e.project(), true));
            }
        }
    }

    private void importTransGroup(DocumentLayout layout, String version,
            List<PushPlanService.PlanEntry> group, Map<String, byte[]> files,
            boolean force, HAccount actor, List<Imported> done)
            throws IOException {
        for (PushPlanService.PlanEntry e : group) {
            byte[] bytes = files.get(e.path());
            if (bytes == null) {
                continue;
            }
            importService.importTranslations(e.project(), version, e.docId(),
                    e.localeId(), layout.readTranslation(bytes), actor, force);
            done.add(new Imported(e.path(), e.docId(), e.localeId(),
                    e.project(), false));
        }
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
