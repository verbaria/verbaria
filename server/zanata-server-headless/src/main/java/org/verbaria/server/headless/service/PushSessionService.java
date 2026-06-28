package org.verbaria.server.headless.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.verbaria.server.api.PushImportedEntry;
import org.verbaria.server.api.PushStatus;
import org.verbaria.server.api.PushStatus.State;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.service.PushArchiveService.DocWork;
import org.verbaria.server.headless.service.PushArchiveService.Prepared;

@Service
public class PushSessionService {

    private static final class PushSession {
        final String project;
        final List<String> targetLocales;
        volatile State status = State.RUNNING;
        volatile int total;
        final AtomicInteger processed = new AtomicInteger();
        final List<PushImportedEntry> imported =
                Collections.synchronizedList(new ArrayList<>());
        volatile String error;
        volatile Map<String, Object> lock;

        PushSession(String project, List<String> targetLocales) {
            this.project = project;
            this.targetLocales = targetLocales;
        }
    }

    private final PushArchiveService pushArchiveService;
    private final AccountRepository accountRepository;
    private final LockService lockService;
    private final TransactionTemplate txTemplate;
    private final AsyncTaskExecutor orchestrators;
    private final AsyncTaskExecutor workers;

    private final Map<String, PushSession> sessions = new ConcurrentHashMap<>();

    public PushSessionService(PushArchiveService pushArchiveService,
            AccountRepository accountRepository,
            LockService lockService,
            PlatformTransactionManager txManager,
            @Qualifier("pushOrchestratorExecutor") AsyncTaskExecutor orchestrators,
            @Qualifier("pushWorkerExecutor") AsyncTaskExecutor workers) {
        this.pushArchiveService = pushArchiveService;
        this.accountRepository = accountRepository;
        this.lockService = lockService;
        this.txTemplate = new TransactionTemplate(txManager);
        this.orchestrators = orchestrators;
        this.workers = workers;
    }

    public String start(String type, String pattern, String version,
            List<String> targetLocales, String sourceLang, boolean force,
            byte[] archive, String actorUsername) throws IOException {
        Prepared prepared = pushArchiveService.prepare(type, pattern, version,
                targetLocales, sourceLang, archive);
        String id = UUID.randomUUID().toString();
        PushSession session = new PushSession(pattern, targetLocales);
        session.total = prepared.fileCount();
        sessions.put(id, session);
        orchestrators.submit(() -> run(session, prepared, version, force,
                actorUsername));
        return id;
    }

    public PushStatus status(String id) {
        PushSession session = sessions.get(id);
        if (session == null) {
            return null;
        }
        List<PushImportedEntry> imported;
        synchronized (session.imported) {
            imported = List.copyOf(session.imported);
        }
        return new PushStatus(session.status,
                session.total, session.processed.get(), imported,
                session.status == State.ERROR ? session.error : null,
                session.status == State.DONE ? session.lock : null);
    }

    private void run(PushSession session, Prepared prepared, String version,
            boolean force, String actorUsername) {
        try {
            List<Future<List<PushImportedEntry>>> futures = new ArrayList<>();
            for (DocWork doc : prepared.documents()) {
                futures.add(workers.submit(() -> importDocument(prepared, doc,
                        version, force, actorUsername)));
            }
            for (Future<List<PushImportedEntry>> f : futures) {
                List<PushImportedEntry> result = f.get();
                session.imported.addAll(result);
                session.processed.addAndGet(result.size());
            }
            session.lock = lockService.buildLock(session.project, version,
                    session.targetLocales, false);
            session.status = State.DONE;
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            session.error = cause.getMessage() == null
                    ? cause.toString() : cause.getMessage();
            session.status = State.ERROR;
        }
    }

    private List<PushImportedEntry> importDocument(Prepared prepared,
            DocWork doc, String version, boolean force, String actorUsername) {
        return txTemplate.execute(tx -> {
            var actor = accountRepository.findByUsername(actorUsername)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unknown user: " + actorUsername));
            return pushArchiveService.importDocument(prepared.layout(), version,
                    doc, prepared.files(), force, actor);
        });
    }
}
