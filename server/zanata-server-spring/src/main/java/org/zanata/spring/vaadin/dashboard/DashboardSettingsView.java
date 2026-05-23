package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import org.zanata.model.HAccount;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard/settings", layout = MainLayout.class)
@PageTitle("Settings | Zanata")
@AnonymousAllowed
public class DashboardSettingsView extends VerticalLayout {

    public DashboardSettingsView(AccountRepository accountRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("Settings");

        TabSheet tabs = new TabSheet();
        tabs.setSizeFull();

        tabs.add("Account", buildAccountTab(accountRepository));
        tabs.add("Profile", buildProfileTab(accountRepository));
        tabs.add("Languages", buildEmptyTab());
        tabs.add("Client", buildEmptyTab());

        add(heading, tabs);
    }

    private VerticalLayout buildAccountTab(AccountRepository accountRepository) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        TextField email = new TextField("Email");
        currentAccount(accountRepository).ifPresent(a -> {
            if (a.getPerson() != null) {
                email.setValue(a.getPerson().getEmail() == null ? "" : a.getPerson().getEmail());
            }
        });
        Button update = new Button("Update");

        layout.add(new HorizontalLayout(email, update));
        return layout;
    }

    private VerticalLayout buildProfileTab(AccountRepository accountRepository) {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);

        TextField name = new TextField("Name");
        TextField username = new TextField("Username");
        name.setReadOnly(true);
        username.setReadOnly(true);

        currentAccount(accountRepository).ifPresent(a -> {
            username.setValue(a.getUsername() == null ? "" : a.getUsername());
            if (a.getPerson() != null && a.getPerson().getName() != null) {
                name.setValue(a.getPerson().getName());
            }
        });

        layout.add(name, username);
        return layout;
    }

    private VerticalLayout buildEmptyTab() {
        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.add(new Paragraph("No data"));
        return layout;
    }

    private Optional<HAccount> currentAccount(AccountRepository accountRepository) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return Optional.empty();
        }
        return accountRepository.findByUsername(auth.getName());
    }
}
