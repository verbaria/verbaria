package org.zanata.email;

import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.inject.Produces;
import jakarta.mail.Session;

@ApplicationScoped
class MailSessionProducer {
    @Resource(lookup = "java:jboss/mail/Default")
    private Session session;

    @Produces
    @Dependent
    Session session() {
        return session;
    }
}
