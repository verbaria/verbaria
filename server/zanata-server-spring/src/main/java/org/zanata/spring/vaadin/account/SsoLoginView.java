package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.vaadin.LoginView;

@Route("account/sso")
@PageTitle("Single sign-on | Zanata")
@AnonymousAllowed
public class SsoLoginView extends VerticalLayout {

    public SsoLoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Single sign-on"));
        panel.add(new Paragraph("Redirecting to SSO..."));
        panel.add(new RouterLink("Use password sign-in instead", LoginView.class));
        add(panel);
    }
}
