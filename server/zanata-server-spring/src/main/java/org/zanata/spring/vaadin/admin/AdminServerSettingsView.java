package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.zanata.model.HApplicationConfiguration;
import org.zanata.spring.repository.ApplicationConfigurationRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/server_settings", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminServerSettingsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.serverSettings"; }


    private static final List<String> KEYS = List.of(
            HApplicationConfiguration.KEY_ADMIN_EMAIL,
            HApplicationConfiguration.KEY_ALLOW_ANONYMOUS_USER,
            HApplicationConfiguration.KEY_AUTO_ACCEPT_TRANSLATOR,
            HApplicationConfiguration.KEY_DISPLAY_USER_EMAIL,
            HApplicationConfiguration.KEY_DOMAIN,
            HApplicationConfiguration.KEY_EMAIL_FROM_ADDRESS,
            HApplicationConfiguration.KEY_EMAIL_LOG_EVENTS,
            HApplicationConfiguration.KEY_EMAIL_LOG_LEVEL,
            HApplicationConfiguration.KEY_GRAVATAR_RATING,
            HApplicationConfiguration.KEY_HELP_URL,
            HApplicationConfiguration.KEY_HOST,
            HApplicationConfiguration.KEY_LOG_DESTINATION_EMAIL,
            HApplicationConfiguration.KEY_MAX_ACTIVE_REQ_PER_API_KEY,
            HApplicationConfiguration.KEY_MAX_CONCURRENT_REQ_PER_API_KEY,
            HApplicationConfiguration.KEY_MAX_FILES_PER_UPLOAD,
            HApplicationConfiguration.KEY_PERMITTED_USER_EMAIL_DOMAIN,
            HApplicationConfiguration.KEY_PIWIK_URL,
            HApplicationConfiguration.KEY_PIWIK_IDSITE,
            HApplicationConfiguration.KEY_REGISTER,
            HApplicationConfiguration.KEY_TERMS_CONDITIONS_URL,
            HApplicationConfiguration.KEY_TM_FUZZY_BANDS);

    public AdminServerSettingsView(ApplicationConfigurationRepository repo) {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminServerSettings.heading")));

        Map<String, TextField> fields = new LinkedHashMap<>();
        FormLayout form = new FormLayout();
        for (String key : KEYS) {
            TextField field = new TextField(key);
            String current = repo.findByKey(key)
                    .map(HApplicationConfiguration::getValue)
                    .orElse("");
            field.setValue(current == null ? "" : current);
            fields.put(key, field);
            form.add(field);
        }

        Button save = new Button(getTranslation("adminServerSettings.save"), e -> {
            fields.forEach((key, field) -> {
                HApplicationConfiguration row = repo.findByKey(key).orElseGet(() -> {
                    HApplicationConfiguration n = new HApplicationConfiguration();
                    n.setKey(key);
                    return n;
                });
                row.setValue(field.getValue() == null ? "" : field.getValue());
                repo.save(row);
            });
            Notification.show(getTranslation("adminServerSettings.saved"));
        });

        add(form, save);
    }
}
