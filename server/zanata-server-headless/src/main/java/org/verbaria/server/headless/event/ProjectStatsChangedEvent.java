package org.verbaria.server.headless.event;

/**
 * Published when a project's translations or source change (push import or an
 * editor save/approve/reject/review), so cached translation statistics for that
 * project can be invalidated.
 */
public record ProjectStatsChangedEvent(String projectSlug) {
}
