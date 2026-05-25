package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.vaadin.LoginView;

@Route("account/sso")
@AnonymousAllowed
public class SsoLoginView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.singleSignOn"; }


    public SsoLoginView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("account.sso.title")));
        panel.add(new Paragraph(getTranslation("account.sso.redirecting")));
        panel.add(new RouterLink(getTranslation("account.sso.usePassword"), LoginView.class));
        add(panel);
    }
}
