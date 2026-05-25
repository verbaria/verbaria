package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/single_openid")
@AnonymousAllowed
public class SingleOpenIdView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.signIn"; }


    public SingleOpenIdView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("account.singleOpenId.title")));
        TextField url = new TextField(getTranslation("account.singleOpenId.openIdUrl"));
        url.setValue("https://");
        Button cont = new Button(getTranslation("account.singleOpenId.continue"));
        panel.add(url, cont);
        add(panel);
    }
}
