package org.verbaria.server.ui.vaadin.account;

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
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.verbaria.server.headless.service.AccountRegistrationService;
import org.verbaria.server.ui.vaadin.LoginView;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

@Route("account/register")
@AnonymousAllowed
public class RegisterView extends VerticalLayout
        implements TitleKey, BeforeEnterObserver {

    @Override public String pageTitleKey() { return "page.signUp"; }

    private final AccountRegistrationService registrationService;

    public RegisterView(AccountRegistrationService registrationService) {
        this.registrationService = registrationService;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        H2 heading = new H2(getTranslation("account.register.title"));

        TextField name = new TextField(getTranslation("account.register.name"));
        TextField username = new TextField(getTranslation("account.register.username"));
        username.setHelperText(getTranslation("account.register.usernameHint"));
        EmailField email = new EmailField(getTranslation("account.register.email"));
        PasswordField password = new PasswordField(getTranslation("account.register.password"));
        password.setHelperText(getTranslation("account.register.passwordHint"));

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

        Button submit = new Button(getTranslation("account.register.submit"), e -> {
            if (human.getValue() != null && !human.getValue().isEmpty()) {
                // honeypot — pretend success without doing anything.
                Notification.show(getTranslation("account.register.success"),
                        3000, Notification.Position.BOTTOM_CENTER);
                UI.getCurrent().navigate(LoginView.class);
                return;
            }
            try {
                AccountRegistrationService.Result result =
                        registrationService.register(username.getValue(),
                                password.getValue(), name.getValue(),
                                email.getValue());
                Notification n = Notification.show(
                        getTranslation(result.enabled()
                                ? "account.register.success"
                                : "account.register.checkEmail"),
                        4000, Notification.Position.BOTTOM_CENTER);
                n.addThemeVariants(NotificationVariant.SUCCESS);
                UI.getCurrent().navigate(LoginView.class);
            } catch (AccountRegistrationService.RegistrationException ex) {
                Notification n = Notification.show(ex.getMessage(), 4500,
                        Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.ERROR);
            } catch (RuntimeException ex) {
                Notification n = Notification.show(
                        getTranslation("account.register.failed", ex.getMessage()),
                        4500, Notification.Position.MIDDLE);
                n.addThemeVariants(NotificationVariant.ERROR);
            }
        });
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submit.setWidthFull();

        Paragraph signInHint = new Paragraph();
        signInHint.add(new com.vaadin.flow.component.html.Span(getTranslation("account.register.alreadyHaveAccount") + " "));
        signInHint.add(new RouterLink(getTranslation("account.register.signIn"), LoginView.class));
        signInHint.addClassNames(AuraUtility.TextAlignment.CENTER, AuraUtility.FontSize.SMALL);

        panel.add(heading, form, submit, signInHint);
        add(panel);
    }

    /**
     * When self-service registration is disabled, replace the form with a
     * notice and a link back to sign-in (the service also rejects the request,
     * so this is the user-facing half of the gate).
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (registrationService.isRegistrationAllowed()) {
            return;
        }
        removeAll();
        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("account.register.title")));
        panel.add(new Paragraph(getTranslation("account.register.disabled")));
        panel.add(new RouterLink(getTranslation("account.register.signIn"),
                LoginView.class));
        add(panel);
    }
}
