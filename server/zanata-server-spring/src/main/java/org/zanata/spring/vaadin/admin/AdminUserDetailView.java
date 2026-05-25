package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.model.HPerson;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/user/:username", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminUserDetailView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.user"; }


    private final AccountRepository accountRepository;

    public AdminUserDetailView(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String username = event.getRouteParameters().get("username").orElse("");
        add(new H2(getTranslation("adminUser.heading", username)));
        accountRepository.findByUsername(username).ifPresentOrElse(account -> {
            HPerson person = account.getPerson();
            String name = person != null && person.getName() != null ? person.getName() : "";
            String email = person != null && person.getEmail() != null ? person.getEmail() : "";
            add(new Paragraph(getTranslation("adminUser.name", name)));
            add(new Paragraph(getTranslation("adminUser.email", email)));
        }, () -> add(new Paragraph(getTranslation("adminUser.notFound"))));
    }
}
