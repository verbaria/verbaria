package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route("account/activate")
@PageTitle("Activate account | Zanata")
@AnonymousAllowed
public class ActivateView extends VerticalLayout implements BeforeEnterObserver {

    private final Paragraph keyInfo = new Paragraph();

    public ActivateView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2("Activate your account"), keyInfo);
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("key", List.of());
        String key = values.isEmpty() ? "" : values.get(0);
        keyInfo.setText(key.isEmpty()
                ? "No activation key supplied."
                : "Activation key: " + key);
    }
}
