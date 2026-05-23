package org.zanata.spring.vaadin.admin;

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

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/create_user", layout = MainLayout.class)
@PageTitle("Create User | Zanata")
@RolesAllowed("ADMIN")
public class AdminCreateUserView extends VerticalLayout {

    public AdminCreateUserView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Create User"));

        FormLayout form = new FormLayout();
        TextField name = new TextField("Name");
        TextField username = new TextField("Username");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");
        form.add(name, username, email, password);

        Button create = new Button("Create");
        add(form, create);
    }
}
