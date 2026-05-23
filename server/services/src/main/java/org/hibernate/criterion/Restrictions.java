/*
 * Surviving sliver of org.hibernate.criterion.Restrictions: only the
 * naturalId() factory is still needed by VersionHome / ProjectHome /
 * VersionGroupHome to build a NaturalIdentifier carrier object, which
 * SlugHome later replays through Session.byNaturalId(...).using(...).
 *
 * Everything else (Restrictions.eq, like, etc.) has been ported to JPA
 * Criteria / HQL in the callers.
 */
package org.hibernate.criterion;

public final class Restrictions {
    private Restrictions() {}

    public static NaturalIdentifier naturalId() {
        return new NaturalIdentifier();
    }
}
