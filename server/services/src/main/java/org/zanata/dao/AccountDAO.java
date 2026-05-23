/**
 * Copyright (c) 2010 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied. See http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 */
package org.zanata.dao;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import io.leangen.graphql.annotations.GraphQLArgument;
import io.leangen.graphql.annotations.GraphQLQuery;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.query.Query;

import javax.annotation.Nonnull;
import jakarta.enterprise.context.RequestScoped;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import javax.annotation.Nullable;

import org.zanata.model.HAccount;
import org.zanata.util.PasswordUtil;

/**
 * DAO for {@link HAccount}. Ported from Hibernate 5 Criteria to the JPA
 * Criteria API.
 */
@RequestScoped
public class AccountDAO extends AbstractDAOImpl<HAccount, Long> {

    public static final String REGION = "Account";
    private static final long serialVersionUID = -2710311827560778973L;

    public AccountDAO() {
        super(HAccount.class);
    }

    public AccountDAO(Session session) {
        super(HAccount.class, session);
    }

    public @Nullable HAccount getEnabledByUsername(String username) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<HAccount> cq = cb.createQuery(HAccount.class);
        Root<HAccount> root = cq.from(HAccount.class);
        cq.where(
                cb.equal(root.get("username"), username),
                cb.equal(root.get("enabled"), true)
        );
        return session.createQuery(cq)
                .setHint("org.hibernate.cacheRegion", REGION)
                .setHint("org.hibernate.cacheable", true)
                .setHint("org.hibernate.comment", "AccountDAO.getEnabledByUsername")
                .uniqueResultOptional()
                .orElse(null);
    }

    @GraphQLQuery(name = "account")
    public @Nullable HAccount getByUsername(@GraphQLArgument(name = "username") String username) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<HAccount> cq = cb.createQuery(HAccount.class);
        Root<HAccount> root = cq.from(HAccount.class);
        cq.where(cb.equal(root.get("username"), username));
        return session.createQuery(cq)
                .setHint("org.hibernate.cacheRegion", REGION)
                .setHint("org.hibernate.cacheable", true)
                .setHint("org.hibernate.comment", "AccountDAO.getByUsername")
                .uniqueResultOptional()
                .orElse(null);
    }

    public Optional<HAccount> tryGetByUsername(@Nonnull String username) {
        return Optional.ofNullable(getByUsername(username));
    }

    public HAccount getByEmail(String email) {
        Query<HAccount> q = getSession()
                .createQuery("from HAccount acc where acc.person.email = :email", HAccount.class)
                .setParameter("email", email)
                .setComment("AccountDAO.getByEmail");
        return q.uniqueResult();
    }

    public HAccount getByUsernameAndEmail(String username, String email) {
        Query<HAccount> q = getSession()
                .createQuery(
                        "from HAccount acc where acc.username = :username and acc.person.email = :email",
                        HAccount.class)
                .setParameter("username", username)
                .setParameter("email", email)
                .setComment("AccountDAO.getByUsernameAndEmail");
        return q.uniqueResult();
    }

    public HAccount getByApiKey(String apikey) {
        Session session = getSession();
        CriteriaBuilder cb = session.getCriteriaBuilder();
        CriteriaQuery<HAccount> cq = cb.createQuery(HAccount.class);
        Root<HAccount> root = cq.from(HAccount.class);
        cq.where(cb.equal(root.get("apiKey"), apikey));
        return session.createQuery(cq).uniqueResultOptional().orElse(null);
    }

    public void createApiKey(HAccount account) {
        account.setApiKey(generateAPIKey());
    }

    protected static String generateAPIKey() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return new String(PasswordUtil.encodeHex(bytes));
    }

    // TODO: re-enable Hibernate-Search-based full-text search once HSearch 7
    // port is complete. For now this falls back to a SQL LIKE query.
    public List<HAccount> searchQuery(String searchQuery, int maxResults, int firstResult) {
        String userName = "%" + searchQuery + "%";
        Query<HAccount> q = getSession()
                .createQuery(
                        "from HAccount as a where lower(a.username) like lower(:username)",
                        HAccount.class)
                .setParameter("username", userName);
        if (maxResults > 0) {
            q.setMaxResults(maxResults);
        }
        q.setFirstResult(firstResult);
        q.setComment("AccountDAO.searchQuery/username");
        return q.list();
    }

    public List<String> getUserNames(String filter, int offset, int maxResults) {
        return this.<String>createFilteredQuery(
                "select distinct acc.username from HAccount acc ", filter, String.class)
                .setMaxResults(maxResults)
                .setFirstResult(offset)
                .setComment("accountDAO.getUserNames")
                .list();
    }

    public int getUserCount(String filter) {
        Long count = this.<Long>createFilteredQuery(
                "select count(*) from HAccount acc ", filter, Long.class)
                .setComment("accountDAO.getUserCount")
                .uniqueResult();
        return count == null ? 0 : count.intValue();
    }

    private <R> Query<R> createFilteredQuery(String queryBase, String filter, Class<R> resultType) {
        if (!StringUtils.isEmpty(filter)) {
            queryBase += "inner join acc.person as person "
                    + "where lower(acc.username) like :filter "
                    + "OR lower(person.email) like :filter "
                    + "OR lower(person.name) like :filter";
        }
        Query<R> q = getSession().createQuery(queryBase, resultType);
        q.setCacheable(true);
        if (!StringUtils.isEmpty(filter)) {
            q.setParameter("filter", "%" + filter.toLowerCase() + "%");
        }
        return q;
    }

    public HAccount getByCredentialsId(String credentialsId) {
        Query<HAccount> q = getSession()
                .createQuery(
                        "select c.account from HCredentials c where c.user = :id",
                        HAccount.class)
                .setParameter("id", credentialsId);
        q.setComment("AccountDAO.getByCredentialsId");
        return q.uniqueResult();
    }

    /**
     * Returns all accounts merged into the another one.
     *
     * @param mergedInto the account into which all returned accounts were merged
     * @return accounts that in the past were merged into the given account
     */
    public List<HAccount> getAllMergedAccounts(HAccount mergedInto) {
        Query<HAccount> q = getSession()
                .createQuery(
                        "from HAccount as a where a.mergedInto = :mergedInto",
                        HAccount.class)
                .setParameter("mergedInto", mergedInto)
                .setComment("AccountDAO.getAllMergedAccounts");
        return q.list();
    }
}
