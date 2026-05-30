package org.zanata.spring.config;

import java.util.Properties;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Builds a {@link JavaMailSender} from {@code spring.mail.*} properties.
 *
 * <p>Spring Boot 4 moved mail auto-configuration into a separate module that we
 * don't depend on, so we create the bean here instead. It only exists when
 * {@code spring.mail.host} is set (e.g. via {@code SPRING_MAIL_HOST}); when
 * absent, {@code EmailService} treats email as disabled and registration falls
 * back to immediate activation.</p>
 */
@Configuration
public class MailConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.mail", name = "host")
    public JavaMailSender javaMailSender(
            @Value("${spring.mail.host}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") String auth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}")
                    String startTls) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
        }
        if (!password.isBlank()) {
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", startTls);
        return sender;
    }
}
