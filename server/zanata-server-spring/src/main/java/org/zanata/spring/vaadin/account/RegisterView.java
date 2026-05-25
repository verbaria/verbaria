package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.service.AccountRegistrationService;
import org.zanata.spring.vaadin.LoginView;

@Route("account/register")
@PageTitle("Sign up | Zanata")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    public RegisterView(AccountRegistrationService registrationService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        H2 heading = new H2("Sign up");

        TextField name = new TextField("Name");
        TextField username = new TextField("Username");
        username.setHelperText("3-40 chars: letters, digits, _ . -");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        password.setHelperText("At least 6 characters.");

        // Honeypot field — bots fill every visible input. If this ends up
        // non-empty we silently drop the request (don't even tell them why).
        TextField human = new TextField();
        human.setLabel("human");
        Style style = human.getStyle();
        style.set("display", "none");
        human.setTabIndex(-1);
        human.setVisible(false);

        FormLayout form = new FormLayout(name, username, email, password, human);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        Button submit = new Button("Sign up", e -> {
            if (human.getValue() != null && !human.getValue().isEmpty()) {
                // honeypot — pretend success without doing anything.
                Notification.show("Account created. Please sign in.",
                        3000, Notification.Position.BOTTOM_CENTER);
                UI.getCurrent().navigate(LoginView.class);
                return;
            }
            try {
                registrationService.register(username.getValue(), password.getValue(),
                        name.getValue(), email.getValue());
                Notification n = Notification.show(
                        "Account created. You can sign in now.",
                        3500, Notification.Position.BOTTOM_CENTER);
                n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                UI.getCurrent().navigate(LoginView.class);
            } catch (AccountRegistrationService.RegistrationException ex) {
                Notification n = Notification.show(ex.getMessage(), 4500,
                        Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch (RuntimeException ex) {
                Notification n = Notification.show(
                        "Sign up failed: " + ex.getMessage(),
                        4500, Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidthFull();

        Paragraph signInHint = new Paragraph();
        signInHint.add(new com.vaadin.flow.component.html.Span("Already have an account? "));
        signInHint.add(new RouterLink("Sign in", LoginView.class));
        signInHint.getStyle().set("text-align", "center");
        signInHint.getStyle().set("font-size", "0.9rem");

        panel.add(heading, form, submit, signInHint);
        add(panel);
    }
}
