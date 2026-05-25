package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

@Route("account/create_user")
@RolesAllowed("ADMIN")
public class CreateUserView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.createUser"; }


    public CreateUserView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("createUser.title")));

        FormLayout form = new FormLayout();
        TextField name = new TextField(getTranslation("createUser.name"));
        TextField username = new TextField(getTranslation("createUser.username"));
        EmailField email = new EmailField(getTranslation("createUser.email"));
        PasswordField password = new PasswordField(getTranslation("createUser.password"));
        form.add(name, username, email, password);

        Button create = new Button(getTranslation("createUser.submit"));
        panel.add(form, create);
        add(panel);
    }
}
