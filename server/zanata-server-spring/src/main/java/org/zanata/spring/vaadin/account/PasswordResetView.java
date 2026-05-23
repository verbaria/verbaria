package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/password_reset")
@PageTitle("Set new password | Zanata")
@AnonymousAllowed
public class PasswordResetView extends VerticalLayout {

    public PasswordResetView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        panel.add(new H2("Set new password"));
        PasswordField newPassword = new PasswordField("New password");
        PasswordField confirmPassword = new PasswordField("Confirm password");
        Button reset = new Button("Reset");
        panel.add(newPassword, confirmPassword, reset);

        add(panel);
    }
}
