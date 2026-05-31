package org.verbaria.server.ui.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import jakarta.annotation.security.PermitAll;

import org.verbaria.server.ui.vaadin.LoginView;

@Route("account/logout")
@PermitAll
public class LogoutView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.signedOut"; }


    public LogoutView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("account.logout.signedOut")));
        panel.add(new RouterLink(getTranslation("account.logout.signInAgain"), LoginView.class));
        add(panel);
    }
}
