package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.zanata.model.HApplicationConfiguration;
import org.verbaria.server.headless.repository.ApplicationConfigurationRepository;
import org.verbaria.server.headless.settings.ServerSetting;
import org.verbaria.server.ui.vaadin.MainLayout;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

@Route(value = "admin/server_settings", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminServerSettingsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.serverSettings"; }

    private final ApplicationConfigurationRepository repo;
    /** key -> supplier of the string value to persist on save. */
    private final Map<String, Supplier<String>> values = new LinkedHashMap<>();

    public AdminServerSettingsView(ApplicationConfigurationRepository repo) {
        this.repo = repo;
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminServerSettings.heading")));

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);
        content.setSpacing(true);
        content.setWidthFull();

        // Group the settings by section (order preserved), one card per group.
        Map<String, List<Component>> bySection = new LinkedHashMap<>();
        for (ServerSetting setting : ServerSetting.values()) {
            bySection.computeIfAbsent(setting.section(), k -> new ArrayList<>())
                    .add(field(setting));
        }
        bySection.forEach((section, items) ->
                content.add(block(section, items.toArray(new Component[0]))));

        Scroller scroller = new Scroller(content);
        scroller.setWidthFull();
        add(scroller);
        setFlexGrow(1, scroller);

        Button save = new Button(getTranslation("adminServerSettings.save"), e -> {
            values.forEach((key, valueSupplier) ->
                    persist(key, valueSupplier.get()));
            Notification.show(getTranslation("adminServerSettings.saved"));
        });
        add(save);
    }

    /** Builds the input control for a setting based on its declared type. */
    private Component field(ServerSetting setting) {
        String key = setting.key();
        if (setting.type() == Boolean.class) {
            Checkbox box = new Checkbox(label(key));
            boolean def = Boolean.TRUE.equals(setting.defaultValue());
            box.setValue(stored(key)
                    .map(v -> !"false".equalsIgnoreCase(v.trim()))
                    .orElse(def));
            values.put(key, () -> Boolean.toString(box.getValue()));
            return box;
        }
        if (setting.options() != null) {
            Select<String> select = new Select<>();
            select.setLabel(label(key));
            select.setItems(setting.options());
            String current = stored(key).map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .orElse(String.valueOf(setting.defaultValue()));
            if (!current.isEmpty()) {
                select.setValue(current);
            }
            values.put(key, () -> select.getValue() == null ? "" : select.getValue());
            return select;
        }
        if (setting.type() == Integer.class) {
            IntegerField field = new IntegerField(label(key));
            field.setWidthFull();
            stored(key).ifPresent(v -> {
                try {
                    field.setValue(Integer.valueOf(v.trim()));
                } catch (NumberFormatException ignored) {
                    // leave empty on an unparseable stored value
                }
            });
            hint(field::setPlaceholder, field::setHelperText, setting.defaultValue());
            values.put(key, () ->
                    field.getValue() == null ? "" : field.getValue().toString());
            return field;
        }
        TextField field = new TextField(label(key));
        field.setWidthFull();
        field.setValue(stored(key).orElse(""));
        hint(field::setPlaceholder, field::setHelperText, setting.defaultValue());
        values.put(key, field::getValue);
        return field;
    }

    private void hint(java.util.function.Consumer<String> placeholder,
            java.util.function.Consumer<String> helper, Object defaultValue) {
        String def = defaultValue == null ? "" : String.valueOf(defaultValue);
        if (!def.isEmpty()) {
            placeholder.accept(def);
            helper.accept(getTranslation("serverSettings.default", def));
        }
    }

    private Div block(String titleKey, Component... items) {
        Div card = new Div();
        card.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.Background.BASE, AuraUtility.BoxSizing.BORDER);
        card.getStyle().set("width", "100%");

        H3 title = new H3(getTranslation(titleKey));
        title.addClassNames(AuraUtility.Margin.NONE,
                AuraUtility.Margin.Bottom.MEDIUM);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        for (Component item : items) {
            form.add(item);
        }
        card.add(title, form);
        return card;
    }

    private String label(String key) {
        return getTranslation("serverSettings.field." + key);
    }

    private Optional<String> stored(String key) {
        return repo.findByKey(key).map(HApplicationConfiguration::getValue);
    }

    private void persist(String key, String value) {
        HApplicationConfiguration row = repo.findByKey(key).orElseGet(() -> {
            HApplicationConfiguration n = new HApplicationConfiguration();
            n.setKey(key);
            return n;
        });
        row.setValue(value == null ? "" : value);
        repo.save(row);
    }
}
