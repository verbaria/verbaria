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
package org.zanata.dao;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.query.Query;
import org.hibernate.Session;
import org.hibernate.transform.ResultTransformer;
import org.zanata.common.EntityStatus;
import org.zanata.common.LocaleId;
import org.zanata.model.HLocale;
import org.zanata.rest.editor.dto.LocaleSortField;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class LocaleDAO extends AbstractDAOImpl<HLocale, Long> {

    private static final long serialVersionUID = 1L;

    public LocaleDAO() {
        super(HLocale.class);
    }

    public LocaleDAO(Session session) {
        super(HLocale.class, session);
    }

    public HLocale findByLocaleId(LocaleId locale) {
        return getSession().byNaturalId(HLocale.class)
                .using("localeId", locale).load();
    }

    @SuppressWarnings("unchecked")
    public List<HLocale> findBySimilarLocaleId(LocaleId localeId) {
        Query query = getSession()
                .createQuery("from HLocale l where lower(l.localeId) = :id ")
                .setParameter("id", localeId.getId().toLowerCase(Locale.ROOT))
                .setComment("LocaleDAO.findBySimilarLocaleId");
        return (List<HLocale>) query.list();
    }

    public List<HLocale> findAllActive() {
        // TODO port Hibernate 5 Criteria to JPA Criteria
        throw new UnsupportedOperationException(
                "TODO port Hibernate 5 Criteria to JPA Criteria");
    }

    public List<HLocale> findAllActiveAndEnabledByDefault() {
        // TODO port Hibernate 5 Criteria to JPA Criteria
        throw new UnsupportedOperationException(
                "TODO port Hibernate 5 Criteria to JPA Criteria");
    }

    @SuppressWarnings("unchecked")
    public List<HLocale> find(int offset, int maxResults, String filter,
            List<LocaleSortField> sortFields, boolean onlyActive) {
        Query query = getSession()
                .createQuery(buildResultSearchQuery(filter, sortFields, onlyActive));
        if (StringUtils.isNotBlank(filter)) {
            String escapeFilter = escapeQuery(filter);
            query.setParameter("query", "%" + escapeFilter + "%");
        }
        query.setFirstResult(offset).setComment("LocaleDAO.find");

        if (maxResults != -1) {
            query.setMaxResults(maxResults);
        }
        return (List<HLocale>) query.list();
    }

    public Map<HLocale, Integer> getAllSourceLocalesAndDocCount() {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("select doc.locale as locale, count(*) as count from HDocument doc ")
                .append("where doc.obsolete = false ")
                .append("and doc.projectIteration.status<>:OBSOLETE ")
                .append("and doc.projectIteration.project.status<>:OBSOLETE ")
                .append("group by doc.locale");

        Query query = getSession().createQuery(queryBuilder.toString())
                .setParameter("OBSOLETE", EntityStatus.OBSOLETE)
                .setComment("LocaleDAO.getTranslationLocales");

        return processGetSourceLocalesAndDocCount(query);
    }

    public Map<HLocale, Integer> getProjectSourceLocalesAndDocCount(
            String projectSlug) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("select doc.locale as locale, count(*) as count from HDocument doc ")
                .append("where doc.obsolete = false ")
                .append("and doc.projectIteration.status<>:OBSOLETE ")
                .append("and doc.projectIteration.project.status<>:OBSOLETE ")
                .append("and doc.projectIteration.project.slug =:projectSlug ")
                .append("group by doc.locale");

        Query query = getSession().createQuery(queryBuilder.toString())
                .setParameter("projectSlug", projectSlug)
                .setParameter("OBSOLETE", EntityStatus.OBSOLETE)
                .setComment("ProjectDAO.getTranslationLocales");
        return processGetSourceLocalesAndDocCount(query);
    }

    public Map<HLocale, Integer> getProjectVersionSourceLocalesAndDocCount(
            String projectSlug, String versionSlug) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("select doc.locale as locale, count(*) as count from HDocument doc ")
                .append("where doc.obsolete = false ")
                .append("and doc.projectIteration.status<>:OBSOLETE ")
                .append("and doc.projectIteration.slug =:versionSlug ")
                .append("and doc.projectIteration.project.slug =:projectSlug ")
                .append("group by doc.locale");

        Query query = getSession().createQuery(queryBuilder.toString())
                .setParameter("versionSlug", versionSlug)
                .setParameter("projectSlug", projectSlug)
                .setParameter("OBSOLETE", EntityStatus.OBSOLETE)
                .setComment("ProjectIterationDAO.getTranslationLocales");

        return processGetSourceLocalesAndDocCount(query);
    }

    @SuppressWarnings("unchecked")
    public Map<HLocale, Integer> processGetSourceLocalesAndDocCount(Query query) {
        List<Map.Entry<HLocale, Integer>> list =
                (List<Map.Entry<HLocale, Integer>>) query
                        .setResultTransformer(new LocalesAndDocCountTransformer())
                        .list();
        Map<HLocale, Integer> map = new HashMap<>();
        for (Map.Entry<HLocale, Integer> entry : list) {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    public int countByFind(String filter, boolean onlyActive) {
        Query query = getSession()
                .createQuery(buildCountSearchQuery(filter, null, onlyActive));
        if (StringUtils.isNotBlank(filter)) {
            String escapeFilter = escapeQuery(filter);
            query.setParameter("query", "%" + escapeFilter + "%");
        }
        query.setComment("LocaleDAO.countByFind");
        Object totalCount = query.uniqueResult();
        if (totalCount == null) {
            return 0;
        }
        assert totalCount instanceof Long;
        return ((Long) totalCount).intValue();
    }

    private String buildCountSearchQuery(String filter,
            List<LocaleSortField> sortFields, boolean onlyActive) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("select count(*) from HLocale");
        queryBuilder.append(buildSearchQuery(filter, sortFields, onlyActive));
        return queryBuilder.toString();
    }

    private String buildResultSearchQuery(String filter,
            List<LocaleSortField> sortFields, boolean onlyActive) {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("from HLocale");
        queryBuilder.append(buildSearchQuery(filter, sortFields, onlyActive));
        return queryBuilder.toString();
    }

    private String buildSearchQuery(String filter,
            List<LocaleSortField> sortFields, boolean onlyActive) {
        StringBuilder queryBuilder = new StringBuilder();
        boolean hasCondition = StringUtils.isNotBlank(filter) || onlyActive;
        if (hasCondition) {
            queryBuilder.append(" where");
        }

        boolean joinQuery = false;
        if (StringUtils.isNotBlank(filter)) {
            joinQuery = true;
            queryBuilder.append(" lower(localeId) like lower(:query) escape '!'")
                    .append(" or lower(displayName) like lower(:query) escape '!'")
                    .append(" or lower(nativeName) like lower(:query) escape '!'");
        }
        if (onlyActive) {
            if (joinQuery) {
                queryBuilder.append(" and");
            }
            queryBuilder.append(" active = true");
        }

        if (sortFields != null && !sortFields.isEmpty()) {
            queryBuilder.append(" ORDER BY ");
            List<String> sortQuery = Lists.newArrayList();
            for (LocaleSortField sortField : sortFields) {
                String order = sortField.isAscending() ? " ASC" : " DESC";
                sortQuery.add(sortField.getEntityField() + order);
            }
            queryBuilder.append(Joiner.on(", ").join(sortQuery));
        }
        return queryBuilder.toString();
    }

    public static class LocalesAndDocCountTransformer implements ResultTransformer {

        private static final long serialVersionUID = 1L;

        private static final String LOCALE_COL = "locale";
        private static final String COUNT_COL = "count";

        @Override
        public Map.Entry<HLocale, Integer> transformTuple(Object[] tuple,
                String[] aliases) {
            HLocale hLocale = null;
            Integer count = null;
            int aliasesLength = aliases.length;
            for (int i = 0; i < aliasesLength; i++) {
                String columnName = aliases[i];
                if (LOCALE_COL.equals(columnName)) {
                    hLocale = (HLocale) tuple[i];
                } else if (COUNT_COL.equals(columnName)) {
                    count = Math.toIntExact((Long) tuple[i]);
                }
            }
            return new AbstractMap.SimpleEntry<>(hLocale, count);
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List transformList(List collection) {
            return collection;
        }
    }
}
