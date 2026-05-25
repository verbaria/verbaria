package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/password_reset")
@AnonymousAllowed
public class PasswordResetView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.setNewPassword"; }


    public PasswordResetView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        panel.add(new H2(getTranslation("passwordReset.title")));
        PasswordField newPassword = new PasswordField(getTranslation("passwordReset.newPassword"));
        PasswordField confirmPassword = new PasswordField(getTranslation("passwordReset.confirmPassword"));
        Button reset = new Button(getTranslation("passwordReset.submit"));
        panel.add(newPassword, confirmPassword, reset);

        add(panel);
    }
}
