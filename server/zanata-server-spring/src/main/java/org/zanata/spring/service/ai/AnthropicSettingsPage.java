package org.zanata.spring.service.ai;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;

import static org.zanata.spring.service.ai.AnthropicTranslationProvider.DEFAULT_CLI_BINARY;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.DEFAULT_MODEL;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_API_KEY;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_AUTH_MODE;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_AUTH_TOKEN;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_CLI_BINARY;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_MODEL;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.KEY_NATIVE_CLIENT;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.MODE_API_KEY;
import static org.zanata.spring.service.ai.AnthropicTranslationProvider.MODE_OAUTH;

/**
 * Self-contained settings UI for {@link AnthropicTranslationProvider}.
 *
 * <p>Two axes:
 * <ul>
 *   <li><b>Native client</b> — when ticked, translation runs through the
 *       bundled {@code claude-translate.sh} (the official Claude Code CLI)
 *       using the OAuth token, so a Max/Pro subscription works with no API
 *       billing. When unticked, the HTTP Messages API is used.</li>
 *   <li><b>Authentication</b> (API path only) — API key ({@code x-api-key})
 *       vs OAuth bearer token.</li>
 * </ul>
 * The visible fields adapt to both: native mode shows the OAuth token + CLI
 * binary; API mode shows the auth-mode switch and the matching credential.</p>
 */
class AnthropicSettingsPage extends VerticalLayout {

    AnthropicSettingsPage(AiSettingsService settings, String displayName) {
        setPadding(false);
        setSpacing(true);
        setWidthFull();

        Checkbox nativeClient = new Checkbox(getTranslation("adminAi.nativeClient.label"));
        nativeClient.setValue(Boolean.parseBoolean(settings.get(KEY_NATIVE_CLIENT, "false")));

        RadioButtonGroup<String> mode = new RadioButtonGroup<>();
        mode.setLabel(getTranslation("adminAi.auth.label"));
        mode.setItems(MODE_API_KEY, MODE_OAUTH);
        mode.setItemLabelGenerator(m -> getTranslation(
                MODE_OAUTH.equals(m) ? "adminAi.auth.oauth" : "adminAi.auth.apikey"));
        String savedMode = MODE_OAUTH.equals(settings.get(KEY_AUTH_MODE, MODE_API_KEY))
                ? MODE_OAUTH : MODE_API_KEY;
        mode.setValue(savedMode);

        PasswordField apiKey = new PasswordField(getTranslation("adminAi.field.apikey"));
        apiKey.setHelperText(getTranslation("adminAi.anthropic.apikey.hint"));
        apiKey.setWidthFull();
        String apiKeyVal = settings.getOrNull(KEY_API_KEY);
        if (apiKeyVal != null) apiKey.setValue(apiKeyVal);

        PasswordField authToken = new PasswordField(getTranslation("adminAi.auth.oauth"));
        authToken.setHelperText(getTranslation("adminAi.anthropic.oauth.hint"));
        authToken.setWidthFull();
        String authTokenVal = settings.getOrNull(KEY_AUTH_TOKEN);
        if (authTokenVal != null) authToken.setValue(authTokenVal);

        TextField cliBinary = new TextField(getTranslation("adminAi.nativeClient.binary"));
        cliBinary.setHelperText(getTranslation("adminAi.nativeClient.binary.hint"));
        cliBinary.setWidthFull();
        cliBinary.setPlaceholder(DEFAULT_CLI_BINARY);
        String cliVal = settings.getOrNull(KEY_CLI_BINARY);
        if (cliVal != null) cliBinary.setValue(cliVal);

        TextField model = new TextField(getTranslation("adminAi.field.model"));
        model.setHelperText(getTranslation("adminAi.anthropic.model.hint", DEFAULT_MODEL));
        model.setWidthFull();
        model.setPlaceholder(DEFAULT_MODEL);
        String modelVal = settings.getOrNull(KEY_MODEL);
        if (modelVal != null) model.setValue(modelVal);

        // Native client → always uses the OAuth token (as CLAUDE_CODE_OAUTH_TOKEN)
        // and exposes the CLI binary; the API auth-mode switch is irrelevant.
        // API mode → show the auth-mode radio + the credential for the mode.
        Runnable applyVisibility = () -> {
            boolean useNative = Boolean.TRUE.equals(nativeClient.getValue());
            boolean oauth = MODE_OAUTH.equals(mode.getValue());
            mode.setVisible(!useNative);
            apiKey.setVisible(!useNative && !oauth);
            authToken.setVisible(useNative || oauth);
            cliBinary.setVisible(useNative);
        };
        applyVisibility.run();
        nativeClient.addValueChangeListener(e -> applyVisibility.run());
        mode.addValueChangeListener(e -> applyVisibility.run());

        Button save = new Button(getTranslation("adminAi.saveProvider", displayName), e -> {
            settings.set(KEY_NATIVE_CLIENT, String.valueOf(
                    Boolean.TRUE.equals(nativeClient.getValue())));
            settings.set(KEY_AUTH_MODE, mode.getValue());
            settings.set(KEY_API_KEY, apiKey.getValue());
            settings.set(KEY_AUTH_TOKEN, authToken.getValue());
            settings.set(KEY_CLI_BINARY, cliBinary.getValue());
            settings.set(KEY_MODEL, model.getValue());
            Notification.show(getTranslation("adminAi.providerSaved", displayName), 2500,
                    Notification.Position.BOTTOM_END);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        add(nativeClient, mode, apiKey, authToken, cliBinary, model, save);
    }
}
