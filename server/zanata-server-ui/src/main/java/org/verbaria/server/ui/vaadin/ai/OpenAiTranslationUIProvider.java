package org.verbaria.server.ui.vaadin.ai;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;

import org.verbaria.server.headless.service.ai.AiSettingsService;
import org.verbaria.server.headless.service.ai.TranslationProvider;
import org.verbaria.server.headless.service.ai.TranslationProviderRegistry;

@SpringComponent
public class OpenAiTranslationUIProvider implements TranslationUIProvider {

    private final AiSettingsService settings;
    private final TranslationProviderRegistry registry;

    public OpenAiTranslationUIProvider(AiSettingsService settings,
                                       TranslationProviderRegistry registry) {
        this.settings = settings;
        this.registry = registry;
    }

    @Override
    public String providerId() {
        return "openai";
    }

    @Override
    public Component createSettingsPage() {
        String displayName = registry.byId(providerId())
                .map(TranslationProvider::displayName).orElse("OpenAI");
        return new OpenAiSettingsPage(settings, displayName);
    }
}
