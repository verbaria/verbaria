package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/password_reset_request")
@PageTitle("Reset password | Zanata")
@AnonymousAllowed
public class PasswordResetRequestView extends VerticalLayout {

    public PasswordResetRequestView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        panel.add(new H2("Reset password"));
        EmailField email = new EmailField("Email");
        Button request = new Button("Request");
        panel.add(email, request);

        add(panel);
    }
}
