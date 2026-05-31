package org.verbaria.server.ui.vaadin.ai;

import com.vaadin.flow.component.Component;

public interface TranslationUIProvider {

    String providerId();

    Component createSettingsPage();
}
