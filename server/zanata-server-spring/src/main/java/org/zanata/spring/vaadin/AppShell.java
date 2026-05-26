package org.zanata.spring.vaadin;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * Push transport is enabled so background threads can deliver updates via
 * {@code ui.access(...)} + {@code ui.push()}. MANUAL means the framework
 * never auto-flushes — every push is explicit, which keeps the overhead off
 * regular interactions and isolated to the few places that actually need it
 * (e.g. the AI translation progress dialog).
 */
@Push(PushMode.MANUAL)
@StyleSheet(Aura.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
/* Verbaria site chrome: gradient background + glass-morphism cards.
 * Loaded last so its rules sit on top of the Aura cascade. The old
 * fontello icon font and the legacy GWT-era style.css were dropped —
 * icons now come from the vaadin-line-awesome addon (LineAwesomeIcon),
 * and the only style.css selectors still in use (.zanata-toolbar /
 * .crumbs / .controls) were moved into verbaria.css. */
@StyleSheet("/resources/assets/css/verbaria.css")
public class AppShell implements AppShellConfigurator {
}
