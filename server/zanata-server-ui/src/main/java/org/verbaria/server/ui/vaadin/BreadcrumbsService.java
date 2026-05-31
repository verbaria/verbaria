package org.verbaria.server.ui.vaadin;

import java.util.List;

import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import org.springframework.stereotype.Component;

/**
 * UI-scoped breadcrumb state. Views call {@link #set(Crumb...)} in their
 * constructor (or {@code beforeEnter}) to publish the trail, then
 * {@code MainLayout} reads it via {@link #current()} during
 * {@code afterNavigation} and renders it in the top navbar.
 *
 * <p>Keeping the state on the UI (via {@link ComponentUtil#setData}) — not
 * the session — means switching tabs doesn't bleed breadcrumbs between
 * views, and reload-on-navigate naturally resets the trail.</p>
 */
@Component
public class BreadcrumbsService {

    /** A single trail entry. {@code current=true} renders without a link. */
    public record Crumb(String label, String href, boolean current) {
        public static Crumb of(String label, String href) {
            return new Crumb(label, href, false);
        }
        public static Crumb here(String label) {
            return new Crumb(label, "#", true);
        }
    }

    private static final String DATA_KEY = "zanata.breadcrumbs";

    public void set(Crumb... crumbs) {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ComponentUtil.setData(ui, DATA_KEY,
                crumbs == null ? List.of() : List.of(crumbs));
    }

    public void set(List<Crumb> crumbs) {
        UI ui = UI.getCurrent();
        if (ui == null) return;
        ComponentUtil.setData(ui, DATA_KEY,
                crumbs == null ? List.of() : List.copyOf(crumbs));
    }

    public void clear() {
        UI ui = UI.getCurrent();
        if (ui != null) ComponentUtil.setData(ui, DATA_KEY, null);
    }

    @SuppressWarnings("unchecked")
    public List<Crumb> current() {
        UI ui = UI.getCurrent();
        if (ui == null) return List.of();
        Object data = ComponentUtil.getData(ui, DATA_KEY);
        return data instanceof List<?> list ? (List<Crumb>) list : List.of();
    }
}
