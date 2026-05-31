package org.verbaria.server.ui.vaadin;

/**
 * Marker interface for views that publish breadcrumbs via
 * {@link BreadcrumbsService}.
 *
 * <p>{@link MainLayout} uses this as a hint during {@code afterNavigation}:
 * if the routed view implements {@code HasBreadcrumbs} the layout leaves
 * the trail alone (the view set it). Otherwise the layout clears the trail
 * — so navigating from a project page back to the home view doesn't leave
 * stale crumbs in the navbar.</p>
 */
public interface HasBreadcrumbs {
}
