package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

@Route("account/password_reset_request")
@AnonymousAllowed
public class PasswordResetRequestView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.resetPassword"; }


    public PasswordResetRequestView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);

        panel.add(new H2(getTranslation("account.passwordResetRequest.title")));
        EmailField email = new EmailField(getTranslation("account.passwordResetRequest.email"));
        Button request = new Button(getTranslation("account.passwordResetRequest.request"));
        panel.add(email, request);

        add(panel);
    }
}
