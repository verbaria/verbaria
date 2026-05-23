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

import org.zanata.i18n.Messages;
import org.zanata.i18n.MessagesFactory;
import org.zanata.util.HtmlUtil;

import java.util.List;

import jakarta.mail.Message.RecipientType;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

/**
 * This utility builds email messages, controlled by an HtmlEmailStrategy.
 *
 * Ported from the original Kotlin implementation (HtmlEmailBuilder.kt) which
 * used the kotlinx.html DSL; HTML body content is now produced via plain
 * string concatenation in the strategies and assembled here.
 *
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
public final class MessageBuilders {

    private static final String UTF8 = "UTF-8";

    private MessageBuilders() {
    }

    /**
     * Fills in the provided MimeMessage 'msg' using 'strategy' to select the
     * desired body template and to provide context variable values. Does not
     * actually send the email.
     *
     * @return the same message
     */
    static MimeMessage buildMessage(
            MimeMessage msg,
            HtmlEmailStrategy strategy,
            GeneralEmailContext generalContext,
            MessagesFactory messagesFactory) throws MessagingException {

        // TODO remember users' locales, and customise reasons/body/footer/fromName for each recipient
        // msgs = messagesFactory.getMessages(account.getLocale());
        Messages msgs = messagesFactory.getDefaultLocaleMessages();

        EmailAddressBlock addresses = strategy.getAddresses();
        String subject = strategy.getSubject(msgs);
        List<String> receivedReasons = strategy.getReceivedReasons(msgs);

        if (addresses.fromAddress() != null) {
            msg.setFrom(addresses.fromAddress());
        } else {
            String fromName = msgs.get("jsf.Zanata");
            msg.setFrom(Addresses.getAddress(generalContext.fromEmail(), fromName));
        }

        if (addresses.replyToAddress() != null) {
            msg.setReplyTo(addresses.replyToAddress().toArray(new InternetAddress[0]));
        }
        msg.addRecipients(RecipientType.BCC,
                addresses.toAddresses().toArray(new InternetAddress[0]));
        msg.setSubject(subject, UTF8);

        String bodyContent = strategy.renderBody(generalContext, msgs);
        String body = buildBodyWithFooter(msgs, generalContext.serverURL(),
                receivedReasons, bodyContent);

        // Alternative parts should be added in increasing order of preference,
        // ie the preferred format should be added last.
        MimeMultipart multipart = new MimeMultipart("alternative");
        MimeBodyPart textPart = new MimeBodyPart();
        String text = HtmlUtil.htmlToText(body);
        textPart.setText(text, "UTF-8");
        multipart.addBodyPart(textPart);
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent(body, "text/html; charset=UTF-8");
        multipart.addBodyPart(htmlPart);
        msg.setContent(multipart);
        return msg;
    }

    /**
     * Builds the complete HTML email body (including html, head and body
     * elements). 'bodyContent' is the HTML fragment for the main body which
     * will be followed by the generic footer.
     */
    // TODO: kotlinx.html ported as raw HTML
    private static String buildBodyWithFooter(
            Messages msgs,
            String serverPath,
            List<String> receivedReasons,
            String bodyContent) {
        String serverPathSafe = serverPath == null ? "" : serverPath;
        StringBuilder sb = new StringBuilder();
        sb.append("<html>");
        sb.append("<head>");
        sb.append("<meta charset=\"").append(UTF8).append("\">");
        sb.append("</head>");
        sb.append("<body>");
        sb.append(bodyContent);
        sb.append("<hr>");

        if (receivedReasons != null && !receivedReasons.isEmpty()) {
            sb.append("<p><span>");
            sb.append(escapeHtml(msgs.get("jsf.email.YouAreReceivingThisMailBecause")));
            sb.append("<br>");
            for (String reason : receivedReasons) {
                sb.append(escapeHtml(reason));
                sb.append("<br>");
            }
            sb.append("</span></p>");
        }
        sb.append("<p>");
        sb.append(escapeHtml(msgs.get("jsf.email.GeneratedFromZanataServerAt")));
        sb.append(" ");
        sb.append("<a href=\"").append(escapeAttr(serverPathSafe)).append("\">");
        sb.append(escapeHtml(serverPathSafe));
        sb.append("</a>");
        sb.append("</p>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static String escapeAttr(String s) {
        return escapeHtml(s);
    }
}
