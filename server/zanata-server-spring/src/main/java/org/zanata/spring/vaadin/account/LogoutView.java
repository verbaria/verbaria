package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import org.zanata.spring.vaadin.LoginView;

@Route("account/logout")
@PageTitle("Signed out | Zanata")
@PermitAll
public class LogoutView extends VerticalLayout {

    public LogoutView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Signed out"));
        panel.add(new RouterLink("Sign in again", LoginView.class));
        add(panel);
    }
}
