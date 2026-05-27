package org.zanata.spring.vaadin;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.dependency.Uses;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.theme.aura.Aura;
import de.f0rce.ace.AceEditor;

@Push(PushMode.MANUAL)
@StyleSheet(Aura.STYLESHEET)
@CssImport("./aura-utilities.css")
@CssImport("./verbaria.css")
@CssImport("./@f0rce/lit-ace/themes/ace-aura.css")
@Uses(AceEditor.class)
public class AppShell implements AppShellConfigurator {
}
