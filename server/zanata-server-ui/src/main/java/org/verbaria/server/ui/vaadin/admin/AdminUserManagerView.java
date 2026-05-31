package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.ConfigurableFilterDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.stream.Collectors;

import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/usermanager", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminUserManagerView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.userManager"; }


    public AdminUserManagerView(AdminUserQueryService userQuery) {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("page.userManager")));

        TextField filter = new TextField();
        filter.setPlaceholder(getTranslation("admin.users.filterPlaceholder"));
        filter.setClearButtonVisible(true);
        filter.setWidthFull();
        filter.setValueChangeMode(ValueChangeMode.LAZY);
        filter.setValueChangeTimeout(250);
        add(filter);

        Grid<HAccount> grid = new Grid<>(HAccount.class, false);
        grid.addColumn(HAccount::getUsername)
                .setHeader(getTranslation("admin.users.usernameCol")).setAutoWidth(true);
        grid.addColumn(HAccount::isEnabled)
                .setHeader(getTranslation("admin.users.enabledCol")).setAutoWidth(true);
        grid.addColumn(a -> a.getRoles() == null ? ""
                : a.getRoles().stream()
                        .map(HAccountRole::getName)
                        .collect(Collectors.joining(", ")))
                .setHeader(getTranslation("admin.users.rolesCol"));

        // Server-paged DataProvider — never loads more than the visible page
        // even when there are tens of thousands of accounts. The query service
        // pages at the DB and initialises each page's roles in one extra query
        // within a read-only transaction, so the roles column renders even
        // though the grid flushes after the entities detach.
        CallbackDataProvider<HAccount, String> dp =
                DataProvider.fromFilteringCallbacks(
                        q -> {
                            int page = q.getOffset() / Math.max(1, q.getLimit());
                            return userQuery.findPage(q.getFilter().orElse(""),
                                    page, q.getLimit()).stream();
                        },
                        q -> (int) Math.min(Integer.MAX_VALUE,
                                userQuery.count(q.getFilter().orElse(""))));
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
