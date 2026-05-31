package org.verbaria.server.ui.vaadin;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;

import org.springframework.stereotype.Component;

/**
 * Injects {@code <base href="/">} into every index.html response so that
 * relative asset URLs emitted by Vaadin addons (e.g. the parttio Line Awesome
 * icons whose SVG src is {@code line-awesome/svg/...}) resolve from the app
 * root instead of the current route. This keeps icons working on deep routes
 * like {@code /translate/{project}/{version}/{locale}} without needing any
 * Java-side wrapper around the icons themselves.
 */
@Component
public class BaseHrefInjector implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.addIndexHtmlRequestListener(response -> {
            var head = response.getDocument().head();
            if (head.select("base").isEmpty()) {
                head.prependElement("base").attr("href", "/");
            }
        });
    }
}
