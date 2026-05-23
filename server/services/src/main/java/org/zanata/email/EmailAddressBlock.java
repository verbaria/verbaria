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

import java.util.List;

import javax.annotation.Nullable;

import jakarta.mail.internet.InternetAddress;

/**
 * Holds the email addresses (From, To, Reply-To) for an email to be sent.
 *
 * @param fromAddress    the "From" address which should be used for this email
 *                       (optional) - if absent, the server's configured "From"
 *                       email will be used
 * @param toAddresses    the "To" address(es) which should be used for this email
 * @param replyToAddress the Reply-To address(es) which should be used for this
 *                       email (optional)
 */
public record EmailAddressBlock(
        @Nullable InternetAddress fromAddress,
        List<InternetAddress> toAddresses,
        @Nullable List<InternetAddress> replyToAddress) {

    public EmailAddressBlock(List<InternetAddress> toAddresses) {
        this(null, toAddresses, null);
    }

    public EmailAddressBlock(
            @Nullable InternetAddress fromAddress,
            List<InternetAddress> toAddresses) {
        this(fromAddress, toAddresses, null);
    }
}
