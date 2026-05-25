package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.service.AccountRegistrationService;
import org.zanata.spring.vaadin.LoginView;

@Route("account/inactive_account")
@PageTitle("Account inactive | Zanata")
@AnonymousAllowed
public class InactiveAccountView extends VerticalLayout {

    public InactiveAccountView(AccountRegistrationService registrationService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("460px");
        panel.setPadding(true);

        panel.add(new H2("Account inactive"));
        Paragraph intro = new Paragraph(
                "Your account isn't active yet. If you signed up recently, "
                + "check your inbox for an activation email — or use the form "
                + "below to update your email address and resend it.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        panel.add(intro);

        TextField username = new TextField("Username");
        EmailField newEmail = new EmailField("New email address");

        Button resend = new Button("Resend activation email", e -> {
            if (username.getValue() == null || username.getValue().isBlank()) {
                username.setInvalid(true);
                username.setErrorMessage("Enter your username");
                return;
            }
            registrationService.resendActivation(username.getValue().trim());
            // Always show the same neutral confirmation so we don't reveal
            // which usernames exist.
            Notification n = Notification.show(
                    "If that account exists, an activation email has been queued.",
                    3500, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
        });
        resend.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button update = new Button("Update email & resend", e -> {
            if (username.getValue() == null || username.getValue().isBlank()) {
                username.setInvalid(true);
                username.setErrorMessage("Enter your username");
                return;
            }
            if (newEmail.getValue() == null || newEmail.getValue().isBlank()
                    || newEmail.isInvalid()) {
                newEmail.setInvalid(true);
                newEmail.setErrorMessage("Enter a valid email");
                return;
            }
            boolean ok = registrationService.updateEmailForReactivation(
                    username.getValue().trim(), newEmail.getValue().trim());
            // Same neutral message regardless of outcome (no enumeration).
            Notification n = Notification.show(
                    "If that account exists, the email has been updated and "
                    + "an activation email queued.",
                    4000, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.LUMO_CONTRAST);
            if (ok) registrationService.resendActivation(username.getValue().trim());
        });
        update.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout actions = new HorizontalLayout(resend, update);
        actions.setSpacing(true);

        panel.add(username, newEmail, actions);
        panel.add(new RouterLink("Back to sign in", LoginView.class));
        add(panel);
    }
}
