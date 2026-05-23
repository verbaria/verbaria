/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.zanata.email;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.util.Arrays;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public final class SendEmail {

    private static final Logger log = LoggerFactory.getLogger(SendEmail.class);

    private SendEmail() {
    }

    /**
     * Build message using 'messageBuilder' and send it via JavaMail Transport.
     */
    static Message sendEmail(MessageBuilder messageBuilder) {
        try {
            Message email = messageBuilder.makeMessage();
            logMessage(email);
            Transport.send(email);
            return email;
        } catch (MessagingException e) {
            Throwable rootCause = Throwables.getRootCause(e);
            if (rootCause.getClass() == ConnectException.class
                    && "Connection refused".equals(rootCause.getMessage())) {
                throw new RuntimeException(
                        "The system failed to connect to mail service. Please contact the administrator!",
                        e);
            }
            throw new RuntimeException(e);
        }
    }

    private static void logMessage(Message msg) {
        try {
            // NB the body may contain more sensitive information
            if (log.isInfoEnabled()) {
                log.info(
                        "Sending message with Subject \"{}\" to Recipients {} From {} Reply-To {}",
                        msg.getSubject(),
                        Arrays.toString(msg.getAllRecipients()),
                        Arrays.toString(msg.getFrom()),
                        Arrays.toString(msg.getReplyTo()));
            }
        } catch (Exception e) {
            log.warn("Unable to log MimeMessage", e);
            // but then keep going; we don't want logging problems to
            // break anything here...
        }
    }
}
