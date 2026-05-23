package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/single_openid")
@PageTitle("Sign in | Zanata")
@AnonymousAllowed
public class SingleOpenIdView extends VerticalLayout {

    public SingleOpenIdView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Sign in"));
        TextField url = new TextField("OpenID URL");
        url.setValue("https://");
        Button cont = new Button("Continue");
        panel.add(url, cont);
        add(panel);
    }
}
