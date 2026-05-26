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
@StyleSheet("/resources/fontello/css/fontello.css")
@StyleSheet("/resources/assets/css/style.css")
@StyleSheet("/resources/assets/css/verbaria.css")
public class AppShell implements AppShellConfigurator {
}
