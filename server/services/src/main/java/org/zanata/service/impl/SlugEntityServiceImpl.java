/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
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
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.service.impl;

import jakarta.transaction.Transactional;
import org.hibernate.Session;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.zanata.model.HProjectIteration;
import org.zanata.model.SlugEntityBase;
import org.zanata.service.SlugEntityService;

/**
 * Default implementation of the {@link org.zanata.service.SlugEntityService}
 * interface.
 *
 * @author Carlos Munoz <a
 *         href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
@Named("slugEntityServiceImpl")
@RequestScoped
@Transactional
public class SlugEntityServiceImpl implements SlugEntityService {
    private static final long serialVersionUID = 1035231120720583856L;
    @Inject
    private Session session;

    @Override
    public boolean isSlugAvailable(String slug,
            Class<? extends SlugEntityBase> cls) {
        // Port of Hibernate 5 Criteria to HQL (simpler than JPA Criteria here).
        Long count = session.createQuery(
                "select count(e) from " + cls.getSimpleName()
                        + " e where e.slug = :slug",
                Long.class)
                .setParameter("slug", slug)
                .uniqueResult();
        return count == null || count == 0;
    }

    @Override
    public boolean isProjectIterationSlugAvailable(String slug,
            String projectSlug) {
        Long count = session.createQuery(
                "select count(it) from HProjectIteration it"
                        + " where it.slug = :slug"
                        + " and it.project.slug = :projectSlug",
                Long.class)
                .setParameter("slug", slug)
                .setParameter("projectSlug", projectSlug)
                .uniqueResult();
        return count == null || count == 0;
    }
}
