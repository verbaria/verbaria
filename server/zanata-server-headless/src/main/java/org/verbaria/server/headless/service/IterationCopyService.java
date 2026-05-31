package org.verbaria.server.headless.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.common.ContentState;
import org.zanata.common.EntityStatus;
import org.zanata.model.HDocument;
import org.zanata.model.HLocale;
import org.zanata.model.HProject;
import org.zanata.model.HProjectIteration;
import org.zanata.model.HTextFlow;
import org.zanata.model.HTextFlowTarget;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.repository.ProjectIterationRepository;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;
import org.verbaria.server.headless.repository.TextFlowTargetRepository;

/**
 * Creates new {@link HProjectIteration} rows, with optional deep copy of an
 * existing version's documents and (optionally) translations — the same
 * semantics legacy {@code VersionHomeAction.createVersion} +
 * {@code CopyVersionService} provided.
 */
@Service
public class IterationCopyService {

    private final ProjectRepository projectRepository;
    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;

    public IterationCopyService(ProjectRepository projectRepository,
                                ProjectIterationRepository iterationRepository,
                                DocumentRepository documentRepository,
                                TextFlowRepository textFlowRepository,
                                TextFlowTargetRepository targetRepository,
                                LocaleRepository localeRepository) {
        this.projectRepository = projectRepository;
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
    }

    /** What to copy from a source version into the new one. */
    public enum CopyMode {
        /** Empty version — no documents copied. */
        NONE,
        /** Copy source documents + text flows only (no translations). */
        SOURCE_ONLY,
        /** Copy source documents + text flows + every translation target. */
        SOURCE_AND_TRANSLATIONS
    }

    @Transactional
    public HProjectIteration create(String projectSlug,
                                    String newVersionSlug,
                                    EntityStatus status,
                                    String copyFromVersionSlug,
                                    CopyMode mode) {
        HProject project = projectRepository.findBySlug(projectSlug)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Project not found: " + projectSlug));
        if (iterationRepository.findByProjectAndSlug(projectSlug, newVersionSlug).isPresent()) {
            throw new IllegalStateException(
                    "Version '" + newVersionSlug + "' already exists in project '"
                            + projectSlug + "'");
        }

        HProjectIteration target = new HProjectIteration();
        target.setSlug(newVersionSlug);
        target.setProject(project);
        target.setStatus(status == null ? EntityStatus.ACTIVE : status);
        if (project.getDefaultProjectType() != null) {
            target.setProjectType(project.getDefaultProjectType());
        }
        target = iterationRepository.save(target);

        if (mode == null || mode == CopyMode.NONE
                || copyFromVersionSlug == null || copyFromVersionSlug.isBlank()) {
            return target;
        }

        Optional<HProjectIteration> srcOpt = iterationRepository
                .findFullByProjectAndSlug(projectSlug, copyFromVersionSlug);
        if (srcOpt.isEmpty()) {
            // Source is missing → still return the freshly created (empty)
            // target rather than rolling back; callers can re-trigger copy
            // explicitly later.
            return target;
        }
        deepCopy(srcOpt.get(), target, mode == CopyMode.SOURCE_AND_TRANSLATIONS);
        return target;
    }

    private void deepCopy(HProjectIteration source, HProjectIteration target,
                          boolean withTranslations) {
        List<HDocument> docs = documentRepository.findByVersion(
                source.getProject().getSlug(), source.getSlug());
        for (HDocument srcDoc : docs) {
            HDocument newDoc = new HDocument(srcDoc.getDocId(), srcDoc.getName(),
                    srcDoc.getPath(), srcDoc.getContentType(), srcDoc.getLocale());
            newDoc.setProjectIteration(target);
            newDoc.setRevision(1);

            // Persist the document so it gets an id, then attach text flows
            // through the cascade-mapped collection.
            HDocument savedDoc = documentRepository.save(newDoc);

            List<HTextFlow> srcFlows = textFlowRepository.findByDocument(srcDoc.getId());
            Map<String, HTextFlow> byResId = new LinkedHashMap<>();
            List<HTextFlow> ordered = new ArrayList<>(srcFlows.size());
            int pos = 0;
            for (HTextFlow srcFlow : srcFlows) {
                HTextFlow newFlow = new HTextFlow(savedDoc, srcFlow.getResId());
                newFlow.setContents(srcFlow.getContents() == null
                        ? List.of("") : srcFlow.getContents());
                newFlow.setRevision(1);
                newFlow.setObsolete(false);
                newFlow.setPlural(srcFlow.isPlural());
                newFlow.setPos(pos++);
                byResId.put(newFlow.getResId(), newFlow);
                ordered.add(newFlow);
            }
            savedDoc.getAllTextFlows().putAll(byResId);
            savedDoc.getTextFlows().clear();
            savedDoc.getTextFlows().addAll(ordered);
            HDocument persistedDoc = documentRepository.save(savedDoc);

            if (!withTranslations) continue;

            // Re-read flows so we have managed ids for the new text flows.
            List<HTextFlow> newFlows = textFlowRepository.findByDocument(persistedDoc.getId());
            Map<String, HTextFlow> newByResId = new LinkedHashMap<>();
            for (HTextFlow nf : newFlows) {
                newByResId.put(nf.getResId(), nf);
            }
            for (HTextFlow srcFlow : srcFlows) {
                HTextFlow newFlow = newByResId.get(srcFlow.getResId());
                if (newFlow == null) continue;
                // Replay every existing target across every locale.
                for (HLocale loc : localeRepository.findAll()) {
                    if (loc == null || loc.getLocaleId() == null) continue;
                    Optional<HTextFlowTarget> srcTarget = targetRepository
                            .findByTextFlowAndLocale(srcFlow.getId(), loc.getLocaleId());
                    if (srcTarget.isEmpty()) continue;
                    HTextFlowTarget st = srcTarget.get();
                    if (st.getContents() == null || st.getContents().isEmpty()) continue;
                    HTextFlowTarget newTarget = new HTextFlowTarget(newFlow, loc);
                    newTarget.setContents(st.getContents());
                    newTarget.setState(st.getState() == null
                            ? ContentState.New : st.getState());
                    newTarget.setTextFlowRevision(newFlow.getRevision());
                    targetRepository.save(newTarget);
                }
            }
        }
    }
}
