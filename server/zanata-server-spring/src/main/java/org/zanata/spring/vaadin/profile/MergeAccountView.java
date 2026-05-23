package org.zanata.spring.vaadin.profile;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "profile/merge_account", layout = MainLayout.class)
@PageTitle("Merge account | Zanata")
@PermitAll
public class MergeAccountView extends VerticalLayout {

    public MergeAccountView() {
        setSizeFull();
        setPadding(true);

        add(new H2("Merge account"));
        add(new Paragraph("Sign in to the other account to merge it into the current one."));

        FormLayout form = new FormLayout();
        TextField username = new TextField("Username");
        PasswordField password = new PasswordField("Password");
        form.add(username, password);
        Button merge = new Button("Merge");

        add(form, merge);
    }
}
