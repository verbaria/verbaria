/*
 * Copyright 2026, Verbaria contributors.
 *
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, see the FSF site: http://www.fsf.org.
 */
package org.zanata.client.commands.changelog;

import java.io.File;
import java.util.List;

import org.zanata.client.commands.ConfigurableOptions;

public interface ChangelogOptions extends ConfigurableOptions {
    File getOldLock();

    File getNewLock();

    String getFormat();

    File getOutput();

    List<String> getExcludeAuthors();
}
