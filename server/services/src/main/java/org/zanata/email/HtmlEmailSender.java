package org.zanata.email;

import org.zanata.config.ServerFromEmail;
import org.zanata.i18n.MessagesFactory;
import org.zanata.servlet.annotations.ServerPath;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

@ApplicationScoped
public class HtmlEmailSender {

    private final String serverPath;
    private final String serverFromEmail;
    private final Session mailSession;
    private final MessagesFactory messagesFactory;

    public HtmlEmailSender() {
        // Required no-arg constructor for CDI proxies.
        this(null, null, null, null);
    }

    @Inject
    public HtmlEmailSender(
            @ServerPath String serverPath,
            @ServerFromEmail String serverFromEmail,
            Session mailSession,
            MessagesFactory messagesFactory) {
        this.serverPath = serverPath;
        this.serverFromEmail = serverFromEmail;
        this.mailSession = mailSession;
        this.messagesFactory = messagesFactory;
    }

    public Message sendMessage(HtmlEmailStrategy strategy) {
        GeneralEmailContext generalContext = new GeneralEmailContext(serverPath, serverFromEmail);
        MessageBuilder builder = () -> MessageBuilders.buildMessage(
                new MimeMessage(mailSession),
                strategy,
                generalContext,
                messagesFactory);
        return SendEmail.sendEmail(builder);
    }
}
