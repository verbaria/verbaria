package org.zanata.spring.vaadin;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("login")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.signIn"; }


    private final LoginForm loginForm = new LoginForm();

    public LoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        LoginI18n i18n = LoginI18n.createDefault();
        i18n.getForm().setTitle("Zanata");
        i18n.getForm().setUsername(getTranslation("login.username"));
        i18n.getForm().setPassword(getTranslation("login.password"));
        i18n.getForm().setSubmit(getTranslation("login.submit"));
        i18n.getForm().setForgotPassword(getTranslation("login.forgotPassword"));
        i18n.getErrorMessage().setTitle(getTranslation("login.errorTitle"));
        i18n.getErrorMessage().setMessage(getTranslation("login.errorMessage"));
        loginForm.setI18n(i18n);
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        Anchor register = new Anchor("/account/register", getTranslation("login.createAccount"));

        add(loginForm, register);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters()
                .getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
