package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/openid")
@PageTitle("OpenID sign in | Zanata")
@AnonymousAllowed
public class OpenIdLoginView extends VerticalLayout {

    public OpenIdLoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("OpenID sign in"));
        TextField url = new TextField("OpenID URL");
        Button cont = new Button("Continue");
        panel.add(url, cont);
        add(panel);
    }
}
