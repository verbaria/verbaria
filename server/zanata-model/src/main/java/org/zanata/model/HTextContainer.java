/*
 * Copyright 2012, Red Hat, Inc. and individual contributors
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

package org.zanata.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Transient;

import org.zanata.common.HasContents;

/**
 * @author Sean Flanigan <a
 *         href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 *
 * @see Analyzers
 */
//@GraphQLType(name = "TextContainer")
public abstract class HTextContainer implements HasContents, Serializable {
    private static final long serialVersionUID = 1L;

    private List<String> getContentsToIndex() {
        return getContents();
    }

    /**
     * As of release 1.6, replaced by {@link #getContents()}
     *
     * @return
     */
    @Deprecated
    @Transient
    public String getContent() {
        return getContents() != null && getContents().size() > 0 ? getContents()
                .get(0) : null;
    }

    /**
     * As of release 1.6, replaced by {@link #setContents(String...)}
     *
     * @return
     */
    @Deprecated
    public void setContent(String content) {
        setContents(content);
    }

    @Override
    public void setContents(String... args) {
        setContents(new ArrayList<String>(Arrays.asList(args)));
    }

}
