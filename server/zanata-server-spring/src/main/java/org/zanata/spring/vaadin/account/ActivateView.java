package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

import org.zanata.spring.service.AccountRegistrationService;
import org.zanata.spring.vaadin.LoginView;

@Route("account/activate")
@AnonymousAllowed
public class ActivateView extends VerticalLayout
        implements BeforeEnterObserver, TitleKey {

    @Override public String pageTitleKey() { return "page.activateAccount"; }

    private final AccountRegistrationService registrationService;
    private final Paragraph message = new Paragraph();

    public ActivateView(AccountRegistrationService registrationService) {
        this.registrationService = registrationService;
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("activate.title")), message,
                new RouterLink(getTranslation("account.register.signIn"),
                        LoginView.class));
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("key", List.of());
        String key = values.isEmpty() ? "" : values.get(0);
        if (key.isEmpty()) {
            message.setText(getTranslation("activate.noKey"));
            return;
        }
        boolean activated = registrationService.activate(key);
        message.setText(getTranslation(activated
                ? "activate.success" : "activate.failed"));
    }
}
