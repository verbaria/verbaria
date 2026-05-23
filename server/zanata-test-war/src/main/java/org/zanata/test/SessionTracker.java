package org.zanata.test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.BeforeDestroyed;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.event.Observes;
import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SessionTracker {
    private static final Logger log = LoggerFactory.getLogger(SessionTracker.class);
    private final Set<HttpSession> sessions = new HashSet<>();

    synchronized void sessionCreated(@Observes @Initialized(SessionScoped.class) HttpSession session) {
        log.debug("creating session: {}", session);
        sessions.add(session);
    }

    synchronized void sessionDestroyed(@Observes @BeforeDestroyed(SessionScoped.class) HttpSession session) {
        log.debug("removing session: {}", session);
        sessions.remove(session);
    }

    public synchronized void invalidateAllSessions() {
        List<HttpSession> list = List.copyOf(sessions);
        if (!list.isEmpty()) {
            log.info("invalidating {} session(s)", list.size());
            list.forEach(HttpSession::invalidate);
        }
    }
}
