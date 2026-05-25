package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/stats", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminStatsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.serverStats"; }


    public AdminStatsView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminStats.heading")));
        add(new Paragraph(getTranslation("adminStats.noData")));
    }
}
