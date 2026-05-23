package org.zanata.spring.vaadin;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

@StyleSheet(Aura.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@StyleSheet("/resources/fontello/css/fontello.css")
@StyleSheet("/resources/assets/css/style.css")
public class AppShell implements AppShellConfigurator {
}
