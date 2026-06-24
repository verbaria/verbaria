package org.verbaria.server.headless.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.zanata.model.HDocument;
import org.zanata.model.HTextFlow;
import org.verbaria.server.headless.repository.DocumentRepository;
import org.verbaria.server.headless.repository.TextFlowRepository;

/**
 * Admin maintenance for documents: soft-delete (reversible obsolete flag),
 * restore, and hard-delete (irreversible purge of the document plus all its
 * text flows, targets, history and extensions). Hard delete exists to clean up
 * documents pushed into the wrong project by mistake.
 */
@Service
public class DocumentAdminService {

    private final DocumentRepository documentRepository;
    private final TextFlowRepository textFlowRepository;

    public DocumentAdminService(DocumentRepository documentRepository,
            TextFlowRepository textFlowRepository) {
        this.documentRepository = documentRepository;
        this.textFlowRepository = textFlowRepository;
    }

    /** Reversibly hide a document (obsolete=true). Its content is preserved. */
    @Transactional
    public void softDelete(String projectSlug, String versionSlug, String docId) {
        HDocument doc = require(projectSlug, versionSlug, docId);
        doc.setObsolete(true);
        documentRepository.save(doc);
    }

    /** Bring a soft-deleted document back (obsolete=false). */
    @Transactional
    public void restore(String projectSlug, String versionSlug, String docId) {
        HDocument doc = require(projectSlug, versionSlug, docId);
        doc.setObsolete(false);
        documentRepository.save(doc);
    }

    /**
     * Permanently remove a document and everything under it. Each text flow
     * (obsolete ones included) is deleted via the entity graph, which cascades
     * to its targets — and their history, review comments and comment — plus the
     * flow's own history and extensions. The document row is removed last.
     */
    @Transactional
    public void hardDelete(String projectSlug, String versionSlug, String docId) {
        HDocument doc = require(projectSlug, versionSlug, docId);
        List<HTextFlow> flows = textFlowRepository
                .findAllByDocumentIncludingObsolete(doc.getId());
        // Detach the managed (non-obsolete) collection so orphanRemoval doesn't
        // fight the explicit deletes below.
        doc.getTextFlows().clear();
        for (HTextFlow tf : flows) {
            textFlowRepository.delete(tf);
        }
        textFlowRepository.flush();
        documentRepository.delete(doc);
    }

    private HDocument require(String projectSlug, String versionSlug, String docId) {
        return documentRepository
                .findAnyByVersionAndDocId(projectSlug, versionSlug, docId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Document not found: " + projectSlug + "/" + versionSlug
                                + "/" + docId));
    }
}
