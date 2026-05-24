/*
 * Copyright 2015, Red Hat, Inc. and individual contributors
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
package org.zanata.util;

import org.apache.deltaspike.core.api.provider.BeanManagerProvider;
import org.apache.deltaspike.core.util.ContextUtils;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;

/**
 * Minimal CDI/DeltaSpike version of Seam's Contexts class, compatibility
 * with migrated Seam code.
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
@Deprecated
public class Contexts {

    // Spring Boot has no CDI BeanManager; DeltaSpike's BeanManagerProvider.isActive()
    // triggers a static initializer that fails in this environment. Each of the
    // checks below is wrapped so they return false safely. The legacy CDI path is
    // preserved when a BeanManager *is* present (e.g. zanata-war).

    public static boolean isApplicationContextActive() {
        try {
            return BeanManagerProvider.isActive()
                    && ContextUtils.isContextActive(ApplicationScoped.class);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isSessionContextActive() {
        try {
            return BeanManagerProvider.isActive()
                    && ContextUtils.isContextActive(SessionScoped.class);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isRequestContextActive() {
        try {
            return BeanManagerProvider.isActive()
                    && ContextUtils.isContextActive(RequestScoped.class);
        } catch (Throwable t) {
            return false;
        }
    }

}
