package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

@Route("account/create_user")
@PageTitle("Create user | Zanata")
@RolesAllowed("ADMIN")
public class CreateUserView extends VerticalLayout {

    public CreateUserView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Create user"));

        FormLayout form = new FormLayout();
        TextField name = new TextField("Name");
        TextField username = new TextField("Username");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        form.add(name, username, email, password);

        Button create = new Button("Create");
        panel.add(form, create);
        add(panel);
    }
}
