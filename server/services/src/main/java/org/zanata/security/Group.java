/*
 * Local replacement for java.security.acl.Group, removed in JDK 14.
 * Mirrors the minimal API surface that ZanataIdentity / SimpleGroup use.
 */
package org.zanata.security;

import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

public interface Group extends Principal {

    /**
     * Adds a member to the group.
     * @return true if the member was added, false if already present.
     */
    boolean addMember(Principal user);

    /** Removes a member from the group. */
    boolean removeMember(Principal user);

    /** True when the supplied principal is a member (directly or transitively). */
    boolean isMember(Principal member);

    /** Live enumeration of the group's members. */
    Enumeration<? extends Principal> members();

    /** Convenience implementation used by tests and by SimpleGroup. */
    class Impl implements Group {
        private final String name;
        private final Set<Principal> members = new LinkedHashSet<>();

        public Impl(String name) {
            this.name = name;
        }

        @Override public String getName() { return name; }
        @Override public boolean addMember(Principal user) { return members.add(user); }
        @Override public boolean removeMember(Principal user) { return members.remove(user); }
        @Override public boolean isMember(Principal member) { return members.contains(member); }
        @Override public Enumeration<? extends Principal> members() {
            return Collections.enumeration(members);
        }
    }
}
