package org.zanata.spring.vaadin.project;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "project/view/:slug/about", layout = MainLayout.class)
@AnonymousAllowed
public class ProjectAboutRedirectView extends Composite<Div> implements BeforeEnterObserver {

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        String slug = event.getRouteParameters().get("slug").orElse("");
        event.forwardTo("project/view/" + slug);
    }
}
