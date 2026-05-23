package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.model.HPerson;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/user/:username", layout = MainLayout.class)
@PageTitle("User | Zanata")
@RolesAllowed("ADMIN")
public class AdminUserDetailView extends VerticalLayout implements BeforeEnterObserver {

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
        add(new H2("User: " + username));
        accountRepository.findByUsername(username).ifPresentOrElse(account -> {
            HPerson person = account.getPerson();
            String name = person != null && person.getName() != null ? person.getName() : "";
            String email = person != null && person.getEmail() != null ? person.getEmail() : "";
            add(new Paragraph("Name: " + name));
            add(new Paragraph("Email: " + email));
        }, () -> add(new Paragraph("User not found")));
    }
}
