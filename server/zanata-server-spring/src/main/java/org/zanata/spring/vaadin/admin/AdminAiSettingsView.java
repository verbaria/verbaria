package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.LinkedHashMap;
import java.util.Map;

import org.zanata.spring.service.ai.AiPolicyService;
import org.zanata.spring.service.ai.AiSettingsService;
import org.zanata.spring.service.ai.TranslationProvider;
import org.zanata.spring.service.ai.TranslationProviderRegistry;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/ai-settings", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminAiSettingsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.aiTranslation"; }


    public AdminAiSettingsView(TranslationProviderRegistry registry,
                               AiSettingsService settings,
                               AiPolicyService policy) {
        setWidthFull();
        setHeightFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("overflow-y", "auto");
        getStyle().set("box-sizing", "border-box");

        add(new H2(getTranslation("adminAi.heading")));

        // --- global enable toggle ---
        Div gate = new Div();
        gate.setWidthFull();
        gate.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        gate.getStyle().set("border-radius", "8px");
        gate.getStyle().set("padding", "0.9rem 1.1rem");
        gate.getStyle().set("background", "var(--vaadin-background-color)");
        gate.getStyle().set("margin-bottom", "1rem");
        gate.getStyle().set("box-sizing", "border-box");
        gate.getStyle().set("flex-shrink", "0");
        Checkbox enable = new Checkbox(getTranslation("adminAi.enableGlobal"));
        enable.setValue(policy.isGloballyEnabled());
        enable.addValueChangeListener(e -> {
            policy.setGloballyEnabled(Boolean.TRUE.equals(e.getValue()));
            Notification.show(getTranslation(Boolean.TRUE.equals(e.getValue())
                            ? "adminAi.enabledToast" : "adminAi.disabledToast"),
                    2500, Notification.Position.BOTTOM_END);
        });
        Paragraph gateHint = new Paragraph(getTranslation("adminAi.gateHint"));
        gateHint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        gateHint.getStyle().set("margin", "0.25rem 0 0 0");
        gateHint.getStyle().set("font-size", "0.85rem");
        gate.add(enable, gateHint);
        add(gate);

        Paragraph intro = new Paragraph(getTranslation("adminAi.providersIntro"));
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        add(intro);

        for (TranslationProvider p : registry.all()) {
            add(buildProviderCard(p, settings));
        }
    }

    private Div buildProviderCard(
            TranslationProvider p, AiSettingsService settings) {
        Div card = new Div();
        card.setWidthFull();
        card.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        card.getStyle().set("border-radius", "8px");
        card.getStyle().set("padding", "1rem 1.25rem");
        card.getStyle().set("margin-bottom", "1rem");
        card.getStyle().set("box-sizing", "border-box");
        card.getStyle().set("flex-shrink", "0");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        H3 title = new H3(p.displayName());
        title.getStyle().set("margin", "0");
        title.getStyle().set("flex", "1 1 auto");
        Span status = new Span(getTranslation(p.isAvailable()
                ? "adminAi.configured" : "adminAi.notConfigured"));
        status.getStyle().set("color", p.isAvailable()
                ? "var(--aura-green-text)"
                : "var(--vaadin-text-color-secondary)");
        status.getStyle().set("font-size", "0.85rem");
        header.add(title, status);
        card.add(header);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        Map<String, HasValue<?, String>> bound = new LinkedHashMap<>();
        for (TranslationProvider.SettingField f : p.settings()) {
            HasValue<?, String> field;
            if (f.secret()) {
                PasswordField pw = new PasswordField(f.label());
                pw.setHelperText(f.hint());
                pw.setWidthFull();
                String current = settings.getOrNull(f.key());
                if (current != null) pw.setValue(current);
                field = pw;
                form.add(pw);
            } else {
                TextField tf = new TextField(f.label());
                tf.setHelperText(f.hint());
                tf.setWidthFull();
                tf.setPlaceholder(f.defaultValue());
                String current = settings.getOrNull(f.key());
                if (current != null) tf.setValue(current);
                field = tf;
                form.add(tf);
            }
            bound.put(f.key(), field);
        }
        card.add(form);

        Button save = new Button(getTranslation("adminAi.saveProvider", p.displayName()), e -> {
            bound.forEach((key, field) -> settings.set(key, field.getValue()));
            Notification.show(getTranslation("adminAi.providerSaved", p.displayName()), 2500,
                    Notification.Position.BOTTOM_END);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);
        card.add(save);
        return card;
    }
}
