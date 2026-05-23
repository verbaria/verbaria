package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.vaadin.LoginView;

@Route("account/inactive_account")
@PageTitle("Account inactive | Zanata")
@AnonymousAllowed
public class InactiveAccountView extends VerticalLayout {

    public InactiveAccountView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Account inactive"));
        panel.add(new Paragraph("Your account is not active."));
        panel.add(new RouterLink("Back to sign in", LoginView.class));
        add(panel);
    }
}
