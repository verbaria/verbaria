package org.verbaria.server.headless.web.rest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.PageRequest;
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HGlossaryEntry;
import org.zanata.model.HGlossaryTerm;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.model.HTextFlowTargetHistory;
import org.zanata.model.HTextFlowTargetReviewComment;
import org.verbaria.server.headless.extension.comment.CommentExtensions;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.GlossaryEntryRepository;
import org.verbaria.server.headless.repository.GlossaryTermRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetHistoryRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;
import org.verbaria.server.headless.repository.TextFlowTargetReviewCommentRepository;

/**
 * Backs the React translation editor under /project/translate/**. Reads
 * HDocument / HTextFlow / HTextFlowTarget directly from the database;
 * saves translations by upserting HTextFlowTarget rows.
 */
@RestController
@RequestMapping("/rest")
public class EditorController {

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final TextFlowTargetHistoryRepository historyRepository;
    private final TextFlowTargetReviewCommentRepository reviewCommentRepository;
    private final LocaleRepository localeRepository;
    private final GlossaryEntryRepository glossaryEntryRepository;
    private final GlossaryTermRepository glossaryTermRepository;
    private final CommentExtensions comments;

    public EditorController(ProjectRepository projectRepository,
                            ProjectIterationRepository iterationRepository,
                            DocumentRepository documentRepository,
                            TextFlowRepository textFlowRepository,
                            TextFlowTargetRepository textFlowTargetRepository,
                            TextFlowTargetHistoryRepository historyRepository,
                            TextFlowTargetReviewCommentRepository reviewCommentRepository,
                            LocaleRepository localeRepository,
                            GlossaryEntryRepository glossaryEntryRepository,
                            GlossaryTermRepository glossaryTermRepository,
                            CommentExtensions comments) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.historyRepository = historyRepository;
        this.reviewCommentRepository = reviewCommentRepository;
        this.localeRepository = localeRepository;
        this.glossaryEntryRepository = glossaryEntryRepository;
        this.glossaryTermRepository = glossaryTermRepository;
        this.comments = comments;
    }

    @GetMapping("/project/{projectSlug}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> project(@PathVariable("projectSlug") String projectSlug) {
        return projectRepository.findBySlug(projectSlug).map(p -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", p.getSlug());
            out.put("name", p.getName() == null ? p.getSlug() : p.getName());
            out.put("description", p.getDescription() == null ? "" : p.getDescription());
            out.put("status", p.getStatus() == null ? "ACTIVE" : p.getStatus().name());
            out.put("defaultType", p.getDefaultProjectType() == null
                    ? "Gettext" : p.getDefaultProjectType().name());
            return ResponseEntity.ok(out);
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/project/{projectSlug}/version/{versionSlug}/docs")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> versionDocs(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug) {
        return documentRepository.findByVersion(projectSlug, versionSlug).stream()
                .map(d -> {
                    Map<String, Object> doc = new LinkedHashMap<>();
                    doc.put("name", d.getDocId());
                    doc.put("contentType", d.getContentType() == null ? "application/x-gettext"
                            : d.getContentType().toString());
                    doc.put("lang", d.getLocale() == null || d.getLocale().getLocaleId() == null
                            ? "en-US" : d.getLocale().getLocaleId().getId());
                    doc.put("type", "FILE");
                    return doc;
                })
                .collect(Collectors.toList());
    }

    @org.springframework.web.bind.annotation.RequestMapping(
            value = "/project/{projectSlug}/version/{versionSlug}/doc/{docId}/status/{localeId}",
            method = { org.springframework.web.bind.annotation.RequestMethod.GET,
                       org.springframework.web.bind.annotation.RequestMethod.POST })
    @Transactional(readOnly = true)
    public List<Map<String, Object>> phraseStatus(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug,
            @PathVariable("docId") String docId,
            @PathVariable("localeId") String localeId,
            @RequestBody(required = false) Map<String, Object> filter) {
        Optional<HDocument> doc = documentRepository.findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (doc.isEmpty()) return List.of();
        List<HTextFlow> flows = textFlowRepository.findByDocument(doc.get().getId());
        Map<Long, ContentState> states = targetStatesFor(flows, new LocaleId(localeId));
        List<Map<String, Object>> result = new ArrayList<>(flows.size());
        for (HTextFlow tf : flows) {
            ContentState s = states.getOrDefault(tf.getId(), ContentState.New);
            result.add(Map.of(
                    "id", tf.getId(),
                    "resId", tf.getResId(),
                    "status", s.name(),
                    "wordCount", tf.getWordCount() == null ? 0L : tf.getWordCount()));
        }
        return result;
    }

    @GetMapping("/source+trans/{localeId}")
    @Transactional(readOnly = true)
    public Map<Long, Map<String, Object>> sourcePlusTrans(
            @PathVariable("localeId") String localeId,
            @RequestParam("ids") String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (idList.isEmpty()) return Map.of();

        LocaleId locale = new LocaleId(localeId);
        List<HTextFlow> flows = textFlowRepository.findByIds(idList);
        Map<Long, HTextFlowTarget> targets = textFlowTargetRepository
                .findByTextFlowIdsAndLocale(idList, locale).stream()
                .collect(Collectors.toMap(t -> t.getTextFlow().getId(), t -> t, (a, b) -> a));

        Map<Long, Map<String, Object>> result = new LinkedHashMap<>();
        for (HTextFlow tf : flows) {
            List<String> contents = tf.getContents();
            String content = contents.isEmpty() ? "" : contents.get(0);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("content", content);
            source.put("contents", contents);
            source.put("id", tf.getResId());
            source.put("msgctxt", "");
            source.put("plural", tf.isPlural());
            String srcComment = comments.sourceComment(tf);
            source.put("sourceComment", srcComment == null ? "" : srcComment);
            source.put("sourceFlags", "");
            source.put("sourceReferences", "");
            source.put("wordCount", tf.getWordCount() == null ? 0L : tf.getWordCount());

            HTextFlowTarget t = targets.get(tf.getId());
            List<String> tContents = t == null ? List.of("") : t.getContents();
            if (tContents == null) tContents = List.of("");
            Map<String, Object> trans = new LinkedHashMap<>();
            trans.put("content", tContents.isEmpty() ? "" : tContents.get(0));
            trans.put("contents", tContents);
            trans.put("state", (t == null ? ContentState.New : t.getState()).name());
            trans.put("revision", t == null ? 0 : (t.getVersionNum() == null ? 0 : t.getVersionNum()));

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("source", source);
            entry.put(localeId, trans);
            result.put(tf.getId(), entry);
        }
        return result;
    }

    @PutMapping("/trans/{localeId}")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveTrans(
            @PathVariable("localeId") String localeId,
            @RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        if (!(idObj instanceof Number n)) return ResponseEntity.badRequest().build();
        Long tfId = n.longValue();
        @SuppressWarnings("unchecked")
        List<Object> translations = body.get("translations") instanceof List<?> l
                ? (List<Object>) l : List.of();
        String stateRaw = String.valueOf(body.getOrDefault("status", "Translated"));
        ContentState state = parseState(stateRaw);

        LocaleId locale = new LocaleId(localeId);
        HTextFlow tf = textFlowRepository.findById(tfId).orElse(null);
        if (tf == null) return ResponseEntity.notFound().build();
        HLocale hLocale = localeRepository.findByLocaleId(locale).orElse(null);
        if (hLocale == null) return ResponseEntity.badRequest().build();

        HTextFlowTarget target = textFlowTargetRepository
                .findByTextFlowAndLocale(tfId, locale)
                .orElseGet(() -> {
                    HTextFlowTarget t = new HTextFlowTarget();
                    t.setTextFlow(tf);
                    t.setLocale(hLocale);
                    t.setTextFlowRevision(tf.getRevision());
                    return t;
                });

        List<String> contents = translations.stream()
                .map(o -> o == null ? "" : String.valueOf(o))
                .collect(Collectors.toList());
        if (contents.isEmpty()) contents = List.of("");
        target.setContents(contents);
        target.setState(state);
        HTextFlowTarget saved = textFlowTargetRepository.save(target);
        return ResponseEntity.ok(Map.of(
                "status", saved.getState().name(),
                "revision", saved.getVersionNum() == null ? 0 : saved.getVersionNum()));
    }

    @GetMapping("/stats/project/{projectSlug}/version/{versionSlug}/doc/{docId}/locale/{localeId}")
    @Transactional(readOnly = true)
    public Map<String, Object> docStats(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug,
            @PathVariable("docId") String docId,
            @PathVariable("localeId") String localeId) {
        Optional<HDocument> doc = documentRepository.findByVersionAndDocId(projectSlug, versionSlug, docId);
        if (doc.isEmpty()) {
            return Map.of("total", 0, "approved", 0, "translated", 0,
                    "needReview", 0, "untranslated", 0);
        }
        long total = textFlowRepository.findByDocument(doc.get().getId()).size();
        Map<ContentState, Long> counts = new HashMap<>();
        for (Object[] row : textFlowTargetRepository
                .countByDocAndLocale(doc.get().getId(), new LocaleId(localeId))) {
            counts.put((ContentState) row[0], (Long) row[1]);
        }
        long approved = counts.getOrDefault(ContentState.Approved, 0L);
        long translated = counts.getOrDefault(ContentState.Translated, 0L);
        long needReview = counts.getOrDefault(ContentState.NeedReview, 0L)
                + counts.getOrDefault(ContentState.Rejected, 0L);
        long done = approved + translated + needReview;
        return Map.of(
                "total", total,
                "approved", approved,
                "translated", translated,
                "needReview", needReview,
                "untranslated", Math.max(0, total - done));
    }

    @GetMapping("/transhist/{localeId}/{transUnitId}/{projectSlug}")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> transHistory(
            @PathVariable("localeId") String localeId,
            @PathVariable("transUnitId") long transUnitId,
            @PathVariable("projectSlug") String projectSlug,
            @RequestParam(value = "versionSlug", required = false) String versionSlug) {
        List<HTextFlowTargetHistory> rows = historyRepository
                .findByTextFlowAndLocale(transUnitId, new LocaleId(localeId));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (HTextFlowTargetHistory h : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("versionNum", h.getVersionNum() == null ? 0 : h.getVersionNum());
            row.put("state", h.getState() == null ? ContentState.New.name() : h.getState().name());
            row.put("contents", h.getContents() == null ? List.of() : h.getContents());
            row.put("lastChanged", h.getLastChanged() == null ? null : h.getLastChanged().toString());
            row.put("modifiedBy", h.getLastModifiedBy() == null ? null
                    : (h.getLastModifiedBy().getAccount() == null
                            ? h.getLastModifiedBy().getName()
                            : h.getLastModifiedBy().getAccount().getUsername()));
            out.add(row);
        }
        return out;
    }

    @GetMapping("/locales/ui")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> uiLocales() {
        return localeRepository.findAll().stream()
                .filter(l -> l.getLocaleId() != null)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("localeId", l.getLocaleId().getId());
                    m.put("displayName", l.getDisplayName() == null
                            ? l.getLocaleId().getId() : l.getDisplayName());
                    m.put("nativeName", l.getNativeName() == null
                            ? l.getLocaleId().getId() : l.getNativeName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @GetMapping("/suggestions")
    public List<Object> suggestions(
            @RequestParam(value = "from",       required = false) String from,
            @RequestParam(value = "to",         required = false) String to,
            @RequestParam(value = "searchType", required = false) String searchType) {
        // TM lookup needs Hibernate Search wiring on TransMemoryUnit; return empty until then.
        return List.of();
    }

    @GetMapping("/glossary/search")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> glossarySearch(
            @RequestParam(value = "srcLocale",   required = false) String srcLocale,
            @RequestParam(value = "transLocale", required = false) String transLocale,
            @RequestParam(value = "project",     required = false) String project,
            @RequestParam(value = "searchText",  required = false) String searchText,
            @RequestParam(value = "maxResults",  defaultValue = "20") int maxResults) {
        if (srcLocale == null || transLocale == null || searchText == null || searchText.isBlank()) {
            return List.of();
        }
        int cap = Math.min(Math.max(1, maxResults), 200);
        LocaleId src = new LocaleId(srcLocale);
        LocaleId tgt = new LocaleId(transLocale);
        List<HGlossaryEntry> entries = glossaryEntryRepository.searchByText(
                src, tgt, searchText, PageRequest.of(0, cap));
        List<Map<String, Object>> out = new ArrayList<>(entries.size());
        for (HGlossaryEntry entry : entries) {
            HGlossaryTerm srcTerm = entry.getGlossaryTerms().get(entry.getSrcLocale());
            HGlossaryTerm tgtTerm = entry.getGlossaryTerms().entrySet().stream()
                    .filter(e -> e.getKey() != null && e.getKey().getLocaleId() != null
                            && tgt.equals(e.getKey().getLocaleId()))
                    .map(Map.Entry::getValue)
                    .findFirst().orElse(null);
            if (srcTerm == null || tgtTerm == null) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", entry.getId());
            row.put("source", srcTerm.getContent());
            row.put("target", tgtTerm.getContent());
            row.put("sourceLocale", srcLocale);
            row.put("targetLocale", transLocale);
            out.add(row);
        }
        return out;
    }

    @GetMapping("/glossary/details/{transLocale}")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> glossaryDetails(
            @PathVariable("transLocale") String transLocale,
            @RequestParam(value = "termIds", required = false) String termIds) {
        if (termIds == null || termIds.isBlank()) return List.of();
        List<Long> ids = Arrays.stream(termIds.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::parseLong).collect(Collectors.toList());
        List<HGlossaryTerm> terms = glossaryTermRepository.findAllById(ids);
        List<Map<String, Object>> out = new ArrayList<>(terms.size());
        for (HGlossaryTerm term : terms) {
            HGlossaryEntry entry = term.getGlossaryEntry();
            HGlossaryTerm srcTerm = entry == null ? null
                    : entry.getGlossaryTerms().get(entry.getSrcLocale());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", term.getId());
            row.put("content", term.getContent());
            row.put("comment", term.getComment());
            row.put("sourceContent", srcTerm == null ? null : srcTerm.getContent());
            row.put("sourceComment", srcTerm == null ? null : srcTerm.getComment());
            row.put("lastModifiedBy", term.getLastModifiedBy() == null ? null
                    : (term.getLastModifiedBy().getAccount() == null
                            ? term.getLastModifiedBy().getName()
                            : term.getLastModifiedBy().getAccount().getUsername()));
            row.put("lastModifiedDate", term.getLastChanged() == null ? null
                    : term.getLastChanged().toString());
            out.add(row);
        }
        return out;
    }

    @GetMapping("/project/{projectSlug}/version/{versionSlug}/validators")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> validators(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug) {
        HProjectIteration iter = iterationRepository
                .findByProjectAndSlug(projectSlug, versionSlug).orElse(null);
        Map<String, String> custom = null;
        if (iter != null && iter.getCustomizedValidations() != null
                && !iter.getCustomizedValidations().isEmpty()) {
            custom = iter.getCustomizedValidations();
        } else if (iter != null) {
            HProject project = iter.getProject();
            if (project != null && project.getCustomizedValidations() != null
                    && !project.getCustomizedValidations().isEmpty()) {
                custom = project.getCustomizedValidations();
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        if (custom != null) {
            for (Map.Entry<String, String> e : custom.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", e.getKey());
                row.put("state", e.getValue());
                out.add(row);
            }
        } else {
            // System default ValidationIds (mirrors org.zanata.webtrans.shared.model.ValidationId).
            for (String id : DEFAULT_VALIDATION_IDS) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("state", "Warning");
                out.add(row);
            }
        }
        return out;
    }

    private static final List<String> DEFAULT_VALIDATION_IDS = List.of(
            "HTML_XML", "NEW_LINE", "TAB", "JAVA_VARIABLES",
            "XML_ENTITY", "PRINTF_VARIABLES", "PRINTF_XSI_EXTENSION");

    @GetMapping("/review/trans/{localeId}")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> reviewTrans(@PathVariable("localeId") String localeId) {
        List<HTextFlowTargetReviewComment> rows = reviewCommentRepository
                .findByLocale(new LocaleId(localeId));
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (HTextFlowTargetReviewComment c : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("comment", c.getCommentText());
            row.put("commenterName", c.getCommenterName());
            row.put("creationDate", c.getCreationDate() == null ? null : c.getCreationDate().toString());
            out.add(row);
        }
        return out;
    }

    private Map<Long, ContentState> targetStatesFor(List<HTextFlow> flows, LocaleId locale) {
        if (flows.isEmpty()) return Map.of();
        List<Long> ids = flows.stream().map(HTextFlow::getId).collect(Collectors.toList());
        return textFlowTargetRepository.findByTextFlowIdsAndLocale(ids, locale).stream()
                .collect(Collectors.toMap(
                        t -> t.getTextFlow().getId(),
                        HTextFlowTarget::getState,
                        (a, b) -> a));
    }

    private static ContentState parseState(String raw) {
        if (raw == null) return ContentState.Translated;
        for (ContentState s : ContentState.values()) {
            if (s.name().equalsIgnoreCase(raw)) return s;
        }
        return ContentState.Translated;
    }
}
