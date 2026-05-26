package org.zanata.spring.vaadin;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

@Push(PushMode.MANUAL)
@StyleSheet(Aura.STYLESHEET)
@StyleSheet(Lumo.UTILITY_STYLESHEET)
@CssImport("./verbaria.css")
@CssImport("./@f0rce/lit-ace/themes/ace-aura.css")
public class AppShell implements AppShellConfigurator {
}
