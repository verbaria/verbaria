package org.verbaria.server.ui.vaadin;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.RememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.verbaria.server.headless.service.AccountRegistrationService;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

/**
 * Single entry point for the sign-in / sign-up popover. Views call
 * {@link #open()} or {@link #open(String)} — they never touch the dialog,
 * tabs, or {@link AuthenticationManager} directly. That keeps the UI
 * abstraction stable: swap the dialog implementation here and no caller
 * changes.
 *
 * <p>Both flows authenticate in place via {@link AuthenticationManager},
 * persist the resulting {@link SecurityContext} to the HttpSession, and
 * reload the current page so the layout re-renders with the authenticated
 * user. The sign-in flow never navigates away from the current view.
 */
@org.springframework.stereotype.Component
public class LoginDialogService {

    private final AuthenticationManager authenticationManager;
    private final AccountRegistrationService registrationService;
    private final RememberMeServices rememberMeServices;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public LoginDialogService(AuthenticationManager authenticationManager,
                              AccountRegistrationService registrationService,
                              RememberMeServices rememberMeServices) {
        this.authenticationManager = authenticationManager;
        this.registrationService = registrationService;
        this.rememberMeServices = rememberMeServices;
    }

    /** Open the popover. On success, reload the current page in place. */
    public void open() {
        open(null, false);
    }

    /**
     * Open the popover. On success, navigate to {@code returnPath} (used by
     * the {@code /login} route to honour Spring Security's SavedRequest URL).
     * Pass {@code null} to just reload the current page.
     */
    public void open(String returnPath) {
        open(returnPath, false);
    }

    /**
     * Open the popover in <em>standalone</em> mode — used by the {@code /login}
     * route where the surrounding page is intentionally blank. ESC and outside
     * clicks navigate to the home page instead of leaving the user staring at
     * an empty backdrop with no recovery path.
     */
    public void openStandalone(String returnPath) {
        open(returnPath, true);
    }

    private void open(String returnPath, boolean standalone) {
        UI ui = UI.getCurrent();
        if (ui == null) return;

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(ui.getTranslation("brand.name"));
        dialog.setModal(true);
        dialog.setDraggable(false);
        dialog.setResizable(false);
        dialog.setCloseOnEsc(true);
        dialog.setCloseOnOutsideClick(true);
        dialog.setWidth("420px");

        // In standalone mode (/login route) the surrounding page is blank —
        // dismissing the dialog without a destination leaves the user stuck.
        // Intercept the close action and navigate home so they always land on
        // a real view.
        if (standalone) {
            dialog.addDialogCloseActionListener(e -> {
                dialog.close();
                ui.getPage().setLocation("/");
            });
        }

        boolean allowSignUp = registrationService.isRegistrationAllowed();

        Tabs tabs = new Tabs();
        Tab signInTab = new Tab(ui.getTranslation("login.tabSignIn"));
        Tab signUpTab = new Tab(ui.getTranslation("login.tabSignUp"));
        tabs.add(signInTab);
        if (allowSignUp) {
            tabs.add(signUpTab);
        }
        tabs.setSelectedTab(signInTab);

        VerticalLayout signInPanel = buildSignInPanel(ui, dialog, returnPath);
        VerticalLayout signUpPanel = allowSignUp
                ? buildSignUpPanel(ui, dialog, returnPath) : null;
        if (signUpPanel != null) {
            signUpPanel.setVisible(false);
        }

        tabs.addSelectedChangeListener(e -> {
            boolean signIn = e.getSelectedTab() == signInTab;
            signInPanel.setVisible(signIn);
            if (signUpPanel != null) {
                signUpPanel.setVisible(!signIn);
            }
        });

        dialog.add(tabs, signInPanel);
        if (signUpPanel != null) {
            dialog.add(signUpPanel);
        }
        dialog.open();
    }

    // ------------------------------------------------------------------
    // Sign-in panel
    // ------------------------------------------------------------------
    private VerticalLayout buildSignInPanel(UI ui, Dialog dialog, String returnPath) {
        VerticalLayout panel = sectionLayout();

        TextField username = new TextField(ui.getTranslation("login.username"));
        username.setWidthFull();
        username.setAutofocus(true);
        PasswordField password = new PasswordField(ui.getTranslation("login.password"));
        password.setWidthFull();
        Checkbox rememberMe = new Checkbox(ui.getTranslation("login.rememberMe"));

        Div errorBanner = new Div(ui.getTranslation("login.errorMessage"));
        errorBanner.addClassNames(AuraUtility.TextColor.ERROR, AuraUtility.FontSize.SMALL);
        errorBanner.setVisible(false);

        Button submit = new Button(ui.getTranslation("login.submit"));
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submit.setWidthFull();
        // Visual breathing room — fields and the submit shouldn't touch.
        submit.addClassNames(AuraUtility.Margin.Top.MEDIUM);
        Runnable trySubmit = () -> {
            errorBanner.setVisible(false);
            try {
                authenticate(username.getValue(), password.getValue(),
                        Boolean.TRUE.equals(rememberMe.getValue()));
                dialog.close();
                finishSuccess(ui, returnPath);
            } catch (AuthenticationException ex) {
                errorBanner.setVisible(true);
            }
        };
        submit.addClickListener(e -> trySubmit.run());
        // Enter on either field submits.
        username.addKeyDownListener(Key.ENTER, e -> trySubmit.run());
        password.addKeyDownListener(Key.ENTER, e -> trySubmit.run());

        FormLayout form = new FormLayout(username, password);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        panel.add(form, rememberMe, errorBanner, submit);
        return panel;
    }

    // ------------------------------------------------------------------
    // Sign-up panel
    // ------------------------------------------------------------------
    private VerticalLayout buildSignUpPanel(UI ui, Dialog dialog, String returnPath) {
        VerticalLayout panel = sectionLayout();

        TextField name = new TextField(ui.getTranslation("account.register.name"));
        name.setWidthFull();
        TextField username = new TextField(ui.getTranslation("account.register.username"));
        username.setHelperText(ui.getTranslation("account.register.usernameHint"));
        username.setWidthFull();
        EmailField email = new EmailField(ui.getTranslation("account.register.email"));
        email.setWidthFull();
        PasswordField password = new PasswordField(ui.getTranslation("account.register.password"));
        password.setHelperText(ui.getTranslation("account.register.passwordHint"));
        password.setWidthFull();

        // Honeypot — bots fill every input. Real users never see this.
        TextField honeypot = new TextField();
        honeypot.addClassNames(AuraUtility.Display.HIDDEN);
        honeypot.setTabIndex(-1);
        honeypot.setVisible(false);

        Div errorBanner = new Div();
        errorBanner.addClassNames(AuraUtility.TextColor.ERROR, AuraUtility.FontSize.SMALL,
                AuraUtility.Margin.Vertical.XSMALL);
        errorBanner.setVisible(false);

        Button submit = new Button(ui.getTranslation("account.register.submit"));
        submit.addThemeVariants(ButtonVariant.PRIMARY);
        submit.setWidthFull();
        submit.addClassNames(AuraUtility.Margin.Top.MEDIUM);
        submit.addClickListener(e -> {
            errorBanner.setVisible(false);
            // Drop honeypot-tripped requests silently.
            if (honeypot.getValue() != null && !honeypot.getValue().isEmpty()) {
                dialog.close();
                return;
            }
            try {
                AccountRegistrationService.Result result =
                        registrationService.register(username.getValue(),
                                password.getValue(), name.getValue(),
                                email.getValue());
                if (!result.enabled()) {
                    // Activation required — can't auto-login a disabled account.
                    dialog.close();
                    Notification n = Notification.show(
                            ui.getTranslation("account.register.checkEmail"),
                            4000, Notification.Position.BOTTOM_CENTER);
                    n.addThemeVariants(NotificationVariant.SUCCESS);
                    return;
                }
                // Auto-sign-in after a successful registration so the user
                // lands on the authenticated UI without a second prompt.
                try {
                    authenticate(username.getValue(), password.getValue(), false);
                    dialog.close();
                    finishSuccess(ui, returnPath);
                } catch (AuthenticationException ignore) {
                    dialog.close();
                    Notification n = Notification.show(
                            ui.getTranslation("account.register.success"),
                            3500, Notification.Position.BOTTOM_CENTER);
                    n.addThemeVariants(NotificationVariant.SUCCESS);
                }
            } catch (AccountRegistrationService.RegistrationException ex) {
                errorBanner.setText(ex.getMessage());
                errorBanner.setVisible(true);
            } catch (RuntimeException ex) {
                errorBanner.setText(ui.getTranslation("account.register.failed",
                        ex.getMessage()));
                errorBanner.setVisible(true);
            }
        });

        FormLayout form = new FormLayout(name, username, email, password, honeypot);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        panel.add(form, errorBanner, submit);
        return panel;
    }

    /**
     * Shared layout for both panels — comfortable vertical spacing between
     * the form and the submit button so they don't visually collide.
     */
    private static VerticalLayout sectionLayout() {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setMargin(false);
        col.setSpacing(true);
        col.setWidthFull();
        col.getStyle().set("padding-top", "0.75rem");
        col.getStyle().set("padding-bottom", "0.25rem");
        col.setAlignItems(FlexComponent.Alignment.STRETCH);
        return col;
    }

    // ------------------------------------------------------------------
    // Auth helpers
    // ------------------------------------------------------------------
    private void authenticate(String username, String password, boolean rememberMe) {
        UsernamePasswordAuthenticationToken request =
                UsernamePasswordAuthenticationToken.unauthenticated(username, password);
        Authentication result = authenticationManager.authenticate(request);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        // Persist to the HttpSession so subsequent requests see the auth.
        HttpServletRequest httpReq = currentHttpRequest();
        HttpServletResponse httpResp = currentHttpResponse();
        if (httpReq != null && httpResp != null) {
            securityContextRepository.saveContext(context, httpReq, httpResp);
            // Set the remember-me cookie when the user opted in. Without this
            // call the persistent cookie is never written — Spring Security's
            // RememberMeServices.loginSuccess only fires from the form filter,
            // which we bypass by authenticating programmatically.
            if (rememberMe) {
                httpReq.setAttribute("remember-me", "true");
                rememberMeServices.loginSuccess(httpReq, httpResp, result);
            }
        }
    }

    private static void finishSuccess(UI ui, String returnPath) {
        if (returnPath != null && !returnPath.isBlank()) {
            ui.getPage().setLocation(returnPath);
        } else {
            ui.getPage().reload();
        }
    }

    private static HttpServletRequest currentHttpRequest() {
        var req = VaadinService.getCurrentRequest();
        return req instanceof VaadinServletRequest vsr
                ? vsr.getHttpServletRequest() : null;
    }

    private static HttpServletResponse currentHttpResponse() {
        var resp = VaadinService.getCurrentResponse();
        return resp instanceof VaadinServletResponse vsr
                ? vsr.getHttpServletResponse() : null;
    }
}
