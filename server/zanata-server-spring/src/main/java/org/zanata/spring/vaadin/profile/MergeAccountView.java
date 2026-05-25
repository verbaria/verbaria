package org.zanata.spring.vaadin.profile;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.PermitAll;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "profile/merge_account", layout = MainLayout.class)
@PermitAll
public class MergeAccountView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.mergeAccount"; }


    public MergeAccountView() {
        setSizeFull();
        setPadding(true);

        add(new H2(getTranslation("mergeAccount.title")));
        add(new Paragraph(getTranslation("mergeAccount.intro")));

        FormLayout form = new FormLayout();
        TextField username = new TextField(getTranslation("mergeAccount.username"));
        PasswordField password = new PasswordField(getTranslation("mergeAccount.password"));
        form.add(username, password);
        Button merge = new Button(getTranslation("mergeAccount.submit"));

        add(form, merge);
    }
}
