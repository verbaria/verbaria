package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/register")
@PageTitle("Sign up | Zanata")
@AnonymousAllowed
public class RegisterView extends VerticalLayout {

    public RegisterView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        H2 heading = new H2("Sign up");

        FormLayout form = new FormLayout();
        TextField name = new TextField("Name");
        TextField username = new TextField("Username");
        EmailField email = new EmailField("Email");
        PasswordField password = new PasswordField("Password");

        TextField human = new TextField();
        human.setLabel("human");
        Style style = human.getStyle();
        style.set("display", "none");
        human.setTabIndex(-1);
        human.setVisible(false);

        form.add(name, username, email, password, human);
        Button submit = new Button("Sign up");

        panel.add(heading, form, submit);
        add(panel);
    }
}
