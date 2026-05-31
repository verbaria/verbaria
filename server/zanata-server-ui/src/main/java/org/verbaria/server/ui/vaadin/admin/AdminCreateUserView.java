package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/create_user", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminCreateUserView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.createUser"; }


    public AdminCreateUserView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("createUser.title")));

        FormLayout form = new FormLayout();
        TextField name = new TextField(getTranslation("createUser.name"));
        TextField username = new TextField(getTranslation("createUser.username"));
        EmailField email = new EmailField(getTranslation("createUser.email"));
        PasswordField password = new PasswordField(getTranslation("createUser.password"));
        form.add(name, username, email, password);

        Button create = new Button(getTranslation("createUser.submit"));
        add(form, create);
    }
}
