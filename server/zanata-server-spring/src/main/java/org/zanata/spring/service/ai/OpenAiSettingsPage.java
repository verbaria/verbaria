package org.zanata.spring.service.ai;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import static org.zanata.spring.service.ai.OpenAiTranslationProvider.DEFAULT_BASE;
import static org.zanata.spring.service.ai.OpenAiTranslationProvider.DEFAULT_MODEL;
import static org.zanata.spring.service.ai.OpenAiTranslationProvider.KEY_API_KEY;
import static org.zanata.spring.service.ai.OpenAiTranslationProvider.KEY_BASE;
import static org.zanata.spring.service.ai.OpenAiTranslationProvider.KEY_MODEL;

/**
 * Self-contained settings UI for {@link OpenAiTranslationProvider}: API key,
 * model, and an optional base-URL override for OpenAI-compatible self-hosted
 * endpoints.
 */
class OpenAiSettingsPage extends VerticalLayout {

    OpenAiSettingsPage(AiSettingsService settings, String displayName) {
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        PasswordField apiKey = new PasswordField(getTranslation("adminAi.field.apikey"));
        apiKey.setHelperText(getTranslation("adminAi.openai.apikey.hint"));
        apiKey.setWidthFull();
        String apiKeyVal = settings.getOrNull(KEY_API_KEY);
        if (apiKeyVal != null) apiKey.setValue(apiKeyVal);

        TextField model = new TextField(getTranslation("adminAi.field.model"));
        model.setHelperText(getTranslation("adminAi.openai.model.hint", DEFAULT_MODEL));
        model.setWidthFull();
        model.setPlaceholder(DEFAULT_MODEL);
        String modelVal = settings.getOrNull(KEY_MODEL);
        if (modelVal != null) model.setValue(modelVal);

        TextField base = new TextField(getTranslation("adminAi.field.baseUrl"));
        base.setHelperText(getTranslation("adminAi.openai.baseUrl.hint"));
        base.setWidthFull();
        base.setPlaceholder(DEFAULT_BASE);
        String baseVal = settings.getOrNull(KEY_BASE);
        if (baseVal != null) base.setValue(baseVal);

        Button save = new Button(getTranslation("adminAi.saveProvider", displayName), e -> {
            settings.set(KEY_API_KEY, apiKey.getValue());
            settings.set(KEY_MODEL, model.getValue());
            settings.set(KEY_BASE, base.getValue());
            Notification.show(getTranslation("adminAi.providerSaved", displayName), 2500,
                    Notification.Position.BOTTOM_END);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        add(apiKey, model, base, save);
    }
}
