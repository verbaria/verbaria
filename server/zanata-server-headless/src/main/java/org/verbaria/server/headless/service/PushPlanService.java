package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.adapter.layout.DocumentLayout;
import org.zanata.adapter.layout.PathDoc;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.verbaria.server.api.PushPlan;
import org.verbaria.server.api.PushPlanEntry;
import org.verbaria.server.api.PushPlanUnmatched;
import org.verbaria.server.headless.layout.DocumentLayoutRegistry;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.ProjectRepository;

@Service
public class PushPlanService {

    private final DocumentLayoutRegistry layouts;
    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;

    public PushPlanService(DocumentLayoutRegistry layouts,
            ProjectRepository projectRepository,
            DocumentRepository documentRepository) {
        this.layouts = layouts;
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
    }

    @Transactional(readOnly = true)
    public PushPlan plan(String type, String pattern,
            List<String> paths, List<String> targetLocales, String sourceLang) {
        DocumentLayout layout = layouts.forType(type).orElseThrow(
                () -> new IllegalArgumentException(
                        "No document layout for project type: " + type));
        boolean glob = pattern.indexOf('*') >= 0;
        List<PushPlanEntry> out = new ArrayList<>();
        List<PushPlanUnmatched> unmatched = new ArrayList<>();
        for (String path : paths) {
            Optional<PathDoc> classified = layout.classify(path);
            if (classified.isEmpty()) {
                continue;
            }
            String docId = classified.get().docId();
            String localeId = classified.get().localeId();
            if (glob) {
                String owner = ownerProject(docId, pattern);
                if (owner == null) {
                    unmatched.add(new PushPlanUnmatched(path, docId,
                            "no project matching '" + pattern
                                    + "' owns this document"));
                } else if (localeWanted(localeId, targetLocales)) {
                    out.add(new PushPlanEntry(path, docId, localeId, owner,
                            false));
                }
            } else {
                HProject project = projectRepository.findBySlug(pattern).orElse(null);
                boolean source = isSourceLocale(project, localeId, sourceLang);
                if (source || localeWanted(localeId, targetLocales)) {
                    out.add(new PushPlanEntry(path, docId, localeId, pattern,
                            source));
                }
            }
        }
        return new PushPlan(out, unmatched);
    }

    private static boolean localeWanted(String localeId, List<String> targetLocales) {
        if (targetLocales == null || targetLocales.isEmpty()) {
            return true;
        }
        if (localeId == null) {
            return false;
        }
        for (String wanted : targetLocales) {
            if (localeMatches(localeId, wanted)) {
                return true;
            }
        }
        return false;
    }

    private String ownerProject(String docId, String pattern) {
        for (HDocument d : documentRepository.findByDocIdAcrossProjects(docId)) {
            HProject p = d.getProjectIteration() == null ? null
                    : d.getProjectIteration().getProject();
            if (p != null && matchesPattern(p.getSlug(), pattern)) {
                return p.getSlug();
            }
        }
        return null;
    }

    private static boolean isSourceLocale(HProject project, String localeId,
            String sourceLang) {
        if (localeId == null) {
            return true;
        }
        HLocale src = project == null ? null
                : project.getEffectiveDefaultSourceLocale();
        String srcId = src != null ? src.getLocaleId().getId() : sourceLang;
        return srcId != null && localeMatches(localeId, srcId);
    }

    static boolean matchesPattern(String slug, String pattern) {
        String regex = pattern.replace("**", "*")
                .replace(".", "\\.").replace("*", ".*");
        return slug.matches(regex);
    }

    private static boolean localeMatches(String a, String b) {
        return a.equalsIgnoreCase(b)
                || a.replace('-', '_').equalsIgnoreCase(b.replace('-', '_'))
                || langOnly(a).equalsIgnoreCase(langOnly(b));
    }

    private static String langOnly(String locale) {
        int sep = locale.indexOf('-');
        if (sep < 0) {
            sep = locale.indexOf('_');
        }
        return sep < 0 ? locale : locale.substring(0, sep);
    }
}
