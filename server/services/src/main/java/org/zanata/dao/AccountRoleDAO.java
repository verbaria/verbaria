package org.zanata.dao;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.query.Query;
import org.hibernate.Session;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Named;
import jakarta.validation.constraints.NotNull;

import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HProject;

@Named("accountRoleDAO")
@RequestScoped
public class AccountRoleDAO extends AbstractDAOImpl<HAccountRole, Integer> {

    private static final long serialVersionUID = -4963071695863386845L;

    public AccountRoleDAO() {
        super(HAccountRole.class);
    }

    public AccountRoleDAO(Session session) {
        super(HAccountRole.class, session);
    }

    public boolean roleExists(String role) {
        return findByName(role) != null;
    }

    public HAccountRole findByName(String roleName) {
        // Ported from Hibernate 5 Criteria → HQL.
        return getSession()
                .createQuery("from HAccountRole r where r.name = :name",
                        HAccountRole.class)
                .setParameter("name", roleName)
                .setCacheable(true)
                .setComment("AccountRoleDAO.findByName")
                .uniqueResultOptional()
                .orElse(null);
    }

    public @NotNull HAccountRole create(String roleName, HAccountRole.RoleType type,
            String... includesRoles) {
        HAccountRole role = new HAccountRole();
        role.setName(roleName);
        role.setRoleType(type);
        for (String includeRole : includesRoles) {
            Set<HAccountRole> groups = role.getGroups();
            if (groups == null) {
                groups = new HashSet<HAccountRole>();
                role.setGroups(groups);
            }
            groups.add(findByName(includeRole));
        }
        makePersistent(role);
        return role;
    }

    public HAccountRole updateIncludeRoles(String roleName,
        String... includesRoles) {
        HAccountRole role = findByName(roleName);
        for (String includeRole : includesRoles) {
            Set<HAccountRole> groups = role.getGroups();
            if (groups == null) {
                groups = new HashSet<HAccountRole>();
                role.setGroups(groups);
            }
            groups.add(findByName(includeRole));
        }
        makePersistent(role);
        return role;
    }

    public List<HAccount> listMembers(String roleName) {
        HAccountRole role = findByName(roleName);
        return listMembers(role);
    }

    @SuppressWarnings("unchecked")
    public List<HAccount> listMembers(HAccountRole role) {
        Query query =
                getSession()
                        .createQuery(
                                "from HAccount account where :role member of account.roles");
        query.setParameter("role", role);
        query.setComment("AccountRoleDAO.listMembers");
        return query.list();
    }

    @SuppressWarnings("unchecked")
    public Collection<HAccountRole> getByProject(HProject project) {
        return getSession()
                .createQuery(
                        "select p.allowedRoles from HProject p where p = :project")
                .setParameter("project", project)
                .setComment("AccountRoleDAO.getByProject").list();
    }

}
