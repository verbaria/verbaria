/*
 * Copyright 2026, Verbaria contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.lock;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * The committed sync state written by {@code pull}/{@code push} as
 * {@code verbaria-lock.json}. It records, per document and per locale, a compact
 * signature of the last synced translations so that a later diff (see
 * {@link LockChangelog}) can produce a changelog and commit message without
 * re-inspecting every string.
 *
 * <p>Granularity is deliberately per-file (× locale): the sync unit is the
 * document, the server hands back a per-document {@code revision} and per-target
 * {@code state}/{@code revision}, and a per-key lock would be unreviewable in
 * version control.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "lockVersion", "server", "project", "projectVersion",
        "generatedAt", "documents" })
public class VerbariaLock {

    public static final int CURRENT_VERSION = 1;

    private int lockVersion = CURRENT_VERSION;
    private String server;
    private String project;
    private String projectVersion;
    private String generatedAt;
    /** keyed by docId; TreeMap keeps the JSON output stable for clean diffs. */
    private Map<String, DocumentLock> documents = new TreeMap<>();

    public int getLockVersion() {
        return lockVersion;
    }

    public void setLockVersion(int lockVersion) {
        this.lockVersion = lockVersion;
    }

    public String getServer() {
        return server;
    }

    public void setServer(String server) {
        this.server = server;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Map<String, DocumentLock> getDocuments() {
        return documents;
    }

    public void setDocuments(Map<String, DocumentLock> documents) {
        // normalise to a sorted map regardless of how it was supplied/parsed
        this.documents = documents == null ? new TreeMap<>()
                : new TreeMap<>(documents);
    }

    /** Returns the entry for {@code docId}, creating it if absent. */
    public DocumentLock document(String docId) {
        return documents.computeIfAbsent(docId, k -> new DocumentLock());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "source", "translations" })
    public static class DocumentLock {
        private SourceLock source;
        private Map<String, TranslationLock> translations = new TreeMap<>();

        public SourceLock getSource() {
            return source;
        }

        public void setSource(SourceLock source) {
            this.source = source;
        }

        public Map<String, TranslationLock> getTranslations() {
            return translations;
        }

        public void setTranslations(Map<String, TranslationLock> translations) {
            this.translations = translations == null ? new TreeMap<>()
                    : new TreeMap<>(translations);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "revision" })
    public static class SourceLock {
        /** server-side document revision ({@code ResourceMeta.revision}). */
        private Integer revision;

        public SourceLock() {
        }

        public SourceLock(Integer revision) {
            this.revision = revision;
        }

        public Integer getRevision() {
            return revision;
        }

        public void setRevision(Integer revision) {
            this.revision = revision;
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonPropertyOrder({ "sig", "state", "total", "translators" })
    public static class TranslationLock {
        /** hash over the included {@code (resId, state, revision)} targets. */
        private String sig;
        /** representative (highest) content state present, for display. */
        private String state;
        /** number of targets included in the signature. */
        private Integer total;
        /**
         * Contributors to this document+locale, as {@code "Name <email>"},
         * used to attribute changes with {@code Co-authored-by} trailers in the
         * generated commit message.
         */
        private List<String> translators;

        public TranslationLock() {
        }

        public TranslationLock(String sig, String state, Integer total,
                List<String> translators) {
            this.sig = sig;
            this.state = state;
            this.total = total;
            this.translators = translators;
        }

        public String getSig() {
            return sig;
        }

        public void setSig(String sig) {
            this.sig = sig;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public Integer getTotal() {
            return total;
        }

        public void setTotal(Integer total) {
            this.total = total;
        }

        public List<String> getTranslators() {
            return translators;
        }

        public void setTranslators(List<String> translators) {
            this.translators = translators;
        }
    }
}
