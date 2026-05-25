package org.zanata.spring.vaadin.account;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.List;

@Route("account/activate")
@AnonymousAllowed
public class ActivateView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.activateAccount"; }


    private final Paragraph keyInfo = new Paragraph();

    public ActivateView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        VerticalLayout panel = new VerticalLayout();
        panel.setWidth("420px");
        panel.setPadding(true);
        panel.add(new H2(getTranslation("activate.title")), keyInfo);
        add(panel);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        List<String> values = event.getLocation().getQueryParameters()
                .getParameters().getOrDefault("key", List.of());
        String key = values.isEmpty() ? "" : values.get(0);
        keyInfo.setText(key.isEmpty()
                ? getTranslation("activate.noKey")
                : getTranslation("activate.keyInfo", key));
    }
}
