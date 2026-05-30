package org.zanata.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.zanata.model.HApplicationConfiguration;
import org.zanata.spring.repository.ApplicationConfigurationRepository;
import org.zanata.spring.settings.ServerSetting;

/**
 * Sends transactional emails. The {@link JavaMailSender} bean only exists when
 * {@code spring.mail.host} is configured (see {@code MailConfig}); when absent
 * this service reports {@link #isEnabled()} {@code false} and callers degrade
 * gracefully (e.g. registration activates accounts immediately).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final ApplicationConfigurationRepository configRepository;

    @Value("${verbaria.mail.from:no-reply@verbaria.local}")
    private String defaultFrom;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        ApplicationConfigurationRepository configRepository) {
        this.mailSender = mailSender;
        this.configRepository = configRepository;
    }

    /** True when an SMTP sender is configured and available. */
    public boolean isEnabled() {
        return mailSender.getIfAvailable() != null;
    }

    /** Sends the sign-up activation email. No-op when email is disabled. */
    public void sendActivation(String to, String name, String activationUrl) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Email disabled (no spring.mail.host); "
                    + "activation link for {} not sent: {}", to, activationUrl);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress());
        message.setTo(to);
        message.setSubject("Activate your Verbaria account");
        message.setText("Hi " + (name == null || name.isBlank() ? "there" : name)
                + ",\n\nActivate your Verbaria account by opening this link:\n\n"
                + activationUrl
                + "\n\nIf you did not request this, you can ignore this email.\n");
        sender.send(message);
        log.info("Sent activation email to {}", to);
    }

    /** Configured from-address, falling back to the {@code verbaria.mail.from}. */
    private String fromAddress() {
        return configRepository
                .findByKey(ServerSetting.EMAIL_FROM.key())
                .map(HApplicationConfiguration::getValue)
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .orElse(defaultFrom);
    }
}
