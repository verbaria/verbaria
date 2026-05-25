package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

        TextField filter = new TextField();
        filter.setPlaceholder("Filter by username…");
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        add(filter);

        Grid<HAccount> grid = new Grid<>(HAccount.class, false);
        grid.addColumn(HAccount::getUsername).setHeader("Username").setAutoWidth(true);
        grid.addColumn(HAccount::isEnabled).setHeader("Enabled").setAutoWidth(true);
        grid.addColumn(a -> a.getRoles() == null ? ""
                : a.getRoles().stream()
                        .map(HAccountRole::getName)
                        .collect(Collectors.joining(", ")))
                .setHeader("Roles");

        // Server-paged DataProvider — never loads more than the visible page
        // even when there are tens of thousands of accounts. Filter applied
        // via JpaRepository's paged Specification-free path: case-insensitive
        // username prefix via findUsernameContaining + a count query.
        CallbackDataProvider<HAccount, String> dp =
                DataProvider.fromFilteringCallbacks(
                        q -> {
                            int page = q.getOffset() / Math.max(1, q.getLimit());
                            String text = q.getFilter().orElse("").trim();
                            return (text.isEmpty()
                                    ? accountRepository.findAll(
                                            PageRequest.of(page, q.getLimit(),
                                                    Sort.by("username")))
                                    : accountRepository.findByUsernameContaining(
                                            text,
                                            PageRequest.of(page, q.getLimit(),
                                                    Sort.by("username")))
                            ).stream();
                        },
                        q -> {
                            String text = q.getFilter().orElse("").trim();
                            long n = text.isEmpty()
                                    ? accountRepository.count()
                                    : accountRepository.countByUsernameContaining(text);
                            return (int) Math.min(Integer.MAX_VALUE, n);
                        });
        ConfigurableFilterDataProvider<HAccount, Void, String> filterable =
                dp.withConfigurableFilter();
        filterable.setFilter("");
        grid.setDataProvider(filterable);
        filter.addValueChangeListener(e -> filterable.setFilter(
                e.getValue() == null ? "" : e.getValue()));

        grid.setHeight("70vh");
        grid.addItemClickListener(e -> {
            HAccount a = e.getItem();
            if (a != null && a.getUsername() != null) {
                getUI().ifPresent(ui -> ui.navigate("admin/user/" + a.getUsername()));
            }
        });

        add(grid);
    }
}
