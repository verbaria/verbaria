package org.verbaria.server.headless.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import org.zanata.rest.dto.PushImportedEntry;
import org.zanata.rest.dto.resource.Resource;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.verbaria.server.headless.service.PushPlanService.PlanEntry;

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

    public record DocWork(String project, String docId,
            List<PlanEntry> source, List<List<PlanEntry>> translations) {
        public int fileCount() {
            int n = source.size();
            for (List<PlanEntry> g : translations) {
                n += g.size();
            }
            return n;
        }
    }

    public record Prepared(DocumentLayout layout, Map<String, byte[]> files,
            List<DocWork> documents) {
        public int fileCount() {
            int n = 0;
            for (DocWork d : documents) {
                n += d.fileCount();
            }
            return n;
        }
    }

    public Prepared prepare(ProjectType type, String pattern, String version,
            List<String> targetLocales, String sourceLang, byte[] archive)
            throws IOException {
        Map<String, byte[]> files = unzip(new ByteArrayInputStream(archive));
        DocumentLayout layout = layouts.forType(type).orElseThrow(
                () -> new IllegalArgumentException(
                        "No document layout for project type: " + type));
        List<PlanEntry> plan = pushPlanService.plan(type, pattern,
                new ArrayList<>(files.keySet()), targetLocales, sourceLang);

        Map<String, DocAccum> byDoc = new LinkedHashMap<>();
        for (PlanEntry e : plan) {
            DocAccum acc = byDoc.computeIfAbsent(e.project() + "|" + e.docId(),
                    k -> new DocAccum(e.project(), e.docId()));
            if (e.source()) {
                acc.source.add(e);
            } else {
                acc.transByLocale.computeIfAbsent(e.localeId(),
                        k -> new ArrayList<>()).add(e);
            }
        }
        List<DocWork> documents = new ArrayList<>();
        for (DocAccum acc : byDoc.values()) {
            documents.add(new DocWork(acc.project, acc.docId, acc.source,
                    new ArrayList<>(acc.transByLocale.values())));
        }
        return new Prepared(layout, files, documents);
    }

    private static final class DocAccum {
        final String project;
        final String docId;
        final List<PlanEntry> source = new ArrayList<>();
        final Map<String, List<PlanEntry>> transByLocale = new LinkedHashMap<>();

        DocAccum(String project, String docId) {
            this.project = project;
            this.docId = docId;
        }
    }

    public List<PushImportedEntry> importDocument(DocumentLayout layout,
            String version, DocWork doc, Map<String, byte[]> files,
            boolean force, HAccount actor) {
        List<PushImportedEntry> done = new ArrayList<>();
        if (!doc.source().isEmpty()) {
            done.addAll(importSourceGroup(layout, version, doc.source(), files,
                    force, actor));
        }
        for (List<PlanEntry> group : doc.translations()) {
            done.addAll(importTransGroup(layout, version, group, files, force,
                    actor));
        }
        return done;
    }

    private List<PushImportedEntry> importSourceGroup(DocumentLayout layout,
            String version, List<PlanEntry> group, Map<String, byte[]> files,
            boolean force, HAccount actor) {
        PlanEntry first = group.get(0);
        Map<String, byte[]> groupFiles = new LinkedHashMap<>();
        for (PlanEntry e : group) {
            byte[] bytes = files.get(e.path());
            if (bytes != null) {
                groupFiles.put(e.path(), bytes);
            }
        }
        if (groupFiles.isEmpty()) {
            return List.of();
        }
        Resource resource;
        try {
            resource = layout.readSource(first.docId(), groupFiles);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (first.localeId() != null && !first.localeId().isEmpty()) {
            resource.setLang(new LocaleId(first.localeId()));
        }
        DocumentImportService.ImportResult result = importService.importSource(
                first.project(), version, first.docId(), resource, actor, force);
        String storedLocale = result.document().getLocale() != null
                && result.document().getLocale().getLocaleId() != null
                ? result.document().getLocale().getLocaleId().getId()
                : first.localeId();
        List<PushImportedEntry> done = new ArrayList<>();
        for (PlanEntry e : group) {
            if (files.get(e.path()) != null) {
                done.add(new PushImportedEntry(e.path(), e.docId(), storedLocale,
                        e.project(), true));
            }
        }
        return done;
    }

    private List<PushImportedEntry> importTransGroup(DocumentLayout layout,
            String version, List<PlanEntry> group, Map<String, byte[]> files,
            boolean force, HAccount actor) {
        List<PushImportedEntry> done = new ArrayList<>();
        for (PlanEntry e : group) {
            byte[] bytes = files.get(e.path());
            if (bytes == null) {
                continue;
            }
            try {
                importService.importTranslations(e.project(), version, e.docId(),
                        e.localeId(), layout.readTranslation(bytes), actor,
                        force);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
            done.add(new PushImportedEntry(e.path(), e.docId(), e.localeId(),
                    e.project(), false));
        }
        return done;
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
