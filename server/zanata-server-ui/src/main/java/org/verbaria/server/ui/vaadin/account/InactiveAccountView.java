package org.verbaria.server.ui.vaadin.account;

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
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import org.verbaria.server.headless.service.AccountRegistrationService;
import org.verbaria.server.ui.vaadin.LoginView;

@Route("account/inactive_account")
@AnonymousAllowed
public class InactiveAccountView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.accountInactive"; }


    public InactiveAccountView(AccountRegistrationService registrationService) {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("460px");
        panel.setPadding(true);

        panel.add(new H2(getTranslation("account.inactive.title")));
        Paragraph intro = new Paragraph(getTranslation("account.inactive.intro"));
        intro.addClassNames(AuraUtility.TextColor.SECONDARY);
        panel.add(intro);

        TextField username = new TextField(getTranslation("account.inactive.username"));
        EmailField newEmail = new EmailField(getTranslation("account.inactive.newEmail"));

        Button resend = new Button(getTranslation("account.inactive.resend"), e -> {
            if (username.getValue() == null || username.getValue().isBlank()) {
                username.setInvalid(true);
                username.setErrorMessage(getTranslation("account.inactive.usernameRequired"));
                return;
            }
            registrationService.resendActivation(username.getValue().trim());
            // Always show the same neutral confirmation so we don't reveal
            // which usernames exist.
            Notification n = Notification.show(
                    getTranslation("account.inactive.neutralResend"),
                    3500, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.INFO);
        });
        resend.addThemeVariants(ButtonVariant.TERTIARY);

        Button update = new Button(getTranslation("account.inactive.update"), e -> {
            if (username.getValue() == null || username.getValue().isBlank()) {
                username.setInvalid(true);
                username.setErrorMessage(getTranslation("account.inactive.usernameRequired"));
                return;
            }
            if (newEmail.getValue() == null || newEmail.getValue().isBlank()
                    || newEmail.isInvalid()) {
                newEmail.setInvalid(true);
                newEmail.setErrorMessage(getTranslation("account.inactive.invalidEmail"));
                return;
            }
            boolean ok = registrationService.updateEmailForReactivation(
                    username.getValue().trim(), newEmail.getValue().trim());
            // Same neutral message regardless of outcome (no enumeration).
            Notification n = Notification.show(
                    getTranslation("account.inactive.neutralUpdate"),
                    4000, Notification.Position.BOTTOM_CENTER);
            n.addThemeVariants(NotificationVariant.INFO);
            if (ok) registrationService.resendActivation(username.getValue().trim());
        });
        update.addThemeVariants(ButtonVariant.PRIMARY);

        HorizontalLayout actions = new HorizontalLayout(resend, update);
        actions.setSpacing(true);

        panel.add(username, newEmail, actions);
        panel.add(new RouterLink(getTranslation("account.inactive.backToSignIn"), LoginView.class));
        add(panel);
    }
}
