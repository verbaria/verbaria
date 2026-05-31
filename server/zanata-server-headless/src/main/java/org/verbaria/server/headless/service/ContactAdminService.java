package org.verbaria.server.headless.service;

import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Backing for the "Contact admin" dialog. Until SMTP wiring lands, messages
 * are written to the WARN log (so they show up in admin's server log) and
 * kept in a small in-memory ring buffer so the admin can pull recent ones
 * from a future inbox view.
 */
@Service
public class ContactAdminService {

    private static final Logger log = LoggerFactory.getLogger(ContactAdminService.class);
    private static final int MAX_RETAINED = 200;

    public record Message(Instant when, String fromUsername, String fromEmail,
                          String subject, String body) {}

    private final Deque<Message> recent = new ConcurrentLinkedDeque<>();

    /** Accept a message — never blocks, always returns. */
    public Message send(String fromUsername, String fromEmail,
                        String subject, String body) {
        Message m = new Message(Instant.now(),
                nullSafe(fromUsername), nullSafe(fromEmail),
                nullSafe(subject), nullSafe(body));
        log.warn("Contact-admin from {} <{}>: subject={} body={}",
                m.fromUsername(), m.fromEmail(), m.subject(),
                m.body().replace("\n", " \\n "));
        recent.addFirst(m);
        while (recent.size() > MAX_RETAINED) recent.removeLast();
        return m;
    }

    /** Most recent messages first; never null. */
    public List<Message> recent() {
        return List.copyOf(recent);
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
