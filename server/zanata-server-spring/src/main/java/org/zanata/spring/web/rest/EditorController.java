package org.zanata.spring.web.rest;

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
import org.zanata.common.ContentState;
import org.zanata.common.LocaleId;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.repository.TextFlowRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;

/**
 * Backs the React translation editor under /project/translate/**.  Reads
 * HDocument / HTextFlow / HTextFlowTarget directly from the database; saves
 * translations by upserting HTextFlowTarget rows.  The JSON shapes match
 * what the editor's redux watchers consume from the legacy JAX-RS
 * resources at server/services/src/main/java/org/zanata/rest/editor/*.
 */
@RestController
@RequestMapping("/rest")
public class EditorController {

    private final ProjectRepository projectRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository textFlowTargetRepository;
    private final LocaleRepository localeRepository;

    public EditorController(ProjectRepository projectRepository,
                            DocumentRepository documentRepository,
                            TextFlowRepository textFlowRepository,
                            TextFlowTargetRepository textFlowTargetRepository,
                            LocaleRepository localeRepository) {
        this.projectRepository = projectRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.textFlowTargetRepository = textFlowTargetRepository;
        this.localeRepository = localeRepository;
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
            source.put("sourceComment", tf.getComment() == null ? "" : String.valueOf(tf.getComment()));
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
    public List<Map<String, Object>> transHistory(
            @PathVariable("localeId") String localeId,
            @PathVariable("transUnitId") long transUnitId,
            @PathVariable("projectSlug") String projectSlug,
            @RequestParam(value = "versionSlug", required = false) String versionSlug) {
        return List.of();
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
        return List.of();
    }

    @GetMapping("/glossary/search")
    public List<Object> glossarySearch(
            @RequestParam(value = "srcLocale",   required = false) String srcLocale,
            @RequestParam(value = "transLocale", required = false) String transLocale,
            @RequestParam(value = "project",     required = false) String project,
            @RequestParam(value = "searchText",  required = false) String searchText) {
        return List.of();
    }

    @GetMapping("/glossary/details/{transLocale}")
    public List<Object> glossaryDetails(
            @PathVariable("transLocale") String transLocale,
            @RequestParam(value = "termIds", required = false) String termIds) {
        return List.of();
    }

    @GetMapping("/project/{projectSlug}/version/{versionSlug}/validators")
    public List<Object> validators(
            @PathVariable("projectSlug") String projectSlug,
            @PathVariable("versionSlug") String versionSlug) {
        return List.of();
    }

    @GetMapping("/review/trans/{localeId}")
    public List<Object> reviewTrans(@PathVariable("localeId") String localeId) {
        return List.of();
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
