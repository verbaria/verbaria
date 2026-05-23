package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.stream.Collectors;

import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/usermanager", layout = MainLayout.class)
@PageTitle("User Manager | Zanata")
@RolesAllowed("ADMIN")
public class AdminUserManagerView extends VerticalLayout {

    public AdminUserManagerView(AccountRepository accountRepository) {
        setSizeFull();
        setPadding(true);
        add(new H2("User Manager"));

        Grid<HAccount> grid = new Grid<>(HAccount.class, false);
        grid.addColumn(HAccount::getUsername).setHeader("Username").setAutoWidth(true);
        grid.addColumn(HAccount::isEnabled).setHeader("Enabled").setAutoWidth(true);
        grid.addColumn(a -> a.getRoles() == null ? ""
                : a.getRoles().stream()
                        .map(HAccountRole::getName)
                        .collect(Collectors.joining(", ")))
                .setHeader("Roles");
        grid.setItems(accountRepository.findAll());

        grid.addItemClickListener(e -> {
            HAccount a = e.getItem();
            if (a != null && a.getUsername() != null) {
                getUI().ifPresent(ui -> ui.navigate("admin/user/" + a.getUsername()));
            }
        });

        add(grid);
    }
}
