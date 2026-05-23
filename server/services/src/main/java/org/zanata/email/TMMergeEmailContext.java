/*
 * Copyright 2017, Red Hat, Inc. and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 */
package org.zanata.email;

import org.zanata.common.LocaleId;

import java.util.List;

import jakarta.mail.internet.InternetAddress;

import kotlin.ranges.IntRange;

public final class TMMergeEmailContext {
    private final List<InternetAddress> toAddresses;
    private final ProjectInfo project;
    private final VersionInfo version;
    private final LocaleId locale;
    private final IntRange matchRange;

    public TMMergeEmailContext(List<InternetAddress> toAddresses,
            ProjectInfo project, VersionInfo version, LocaleId locale,
            IntRange matchRange) {
        this.toAddresses = toAddresses;
        this.project = project;
        this.version = version;
        this.locale = locale;
        this.matchRange = matchRange;
    }

    public List<InternetAddress> getToAddresses() { return toAddresses; }
    public ProjectInfo getProject() { return project; }
    public VersionInfo getVersion() { return version; }
    public LocaleId getLocale() { return locale; }
    public IntRange getMatchRange() { return matchRange; }
}
