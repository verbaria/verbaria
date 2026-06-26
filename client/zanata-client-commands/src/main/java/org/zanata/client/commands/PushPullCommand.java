/*
 * Copyright 2011, Red Hat, Inc. and individual contributors
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

package org.zanata.client.commands;

import org.zanata.client.config.LocaleList;
import org.zanata.client.config.LocaleMapping;
import org.zanata.client.exceptions.ConfigException;

public abstract class PushPullCommand<O extends PushPullOptions> extends
        ConfigurableProjectCommand<O> {

    public PushPullCommand(O opts) {
        super(opts);
    }

    public static LocaleList getLocaleMapList(LocaleList projectLocales,
            String[] locales) {
        if (locales == null || locales.length <= 0) {
            return projectLocales;
        } else {
            LocaleList effectiveLocales = new LocaleList();
            for (String locale : locales) {
                boolean foundLocale = false;
                for (LocaleMapping lm : projectLocales) {
                    if (lm.getLocale().equals(locale)
                            || (lm.getMapFrom() != null && lm.getMapFrom()
                                    .equals(locale))) {
                        effectiveLocales.add(lm);
                        foundLocale = true;
                        break;
                    }
                }

                if (!foundLocale) {
                    throw new ConfigException("Specified locale '" + locale
                            + "' was not found! Available locales: "
                            + projectLocales);
                }
            }
            return effectiveLocales;
        }
    }
}
