package org.zanata.spring.vaadin.admin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.model.HAccount;
import org.zanata.model.HAccountRole;
import org.zanata.model.HPerson;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/user/:username", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminUserDetailView extends VerticalLayout
        implements BeforeEnterObserver, TitleKey {

    @Override public String pageTitleKey() { return "page.user"; }

    private final AdminUserQueryService userQuery;

    public AdminUserDetailView(AdminUserQueryService userQuery) {
        this.userQuery = userQuery;
        setSizeFull();
        setPadding(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String username =
                event.getRouteParameters().get("username").orElse("");
        add(new H2(getTranslation("adminUser.heading", username)));

        HAccount account = userQuery.loadWithRoles(username).orElse(null);
        if (account == null) {
            add(new Paragraph(getTranslation("adminUser.notFound")));
            return;
        }

        HPerson person = account.getPerson();
        String name = person != null && person.getName() != null
                ? person.getName() : "";
        String email = person != null && person.getEmail() != null
                ? person.getEmail() : "";
        add(new Paragraph(getTranslation("adminUser.name", name)));
        add(new Paragraph(getTranslation("adminUser.email", email)));

        add(buildRoleEditor(username, account));
    }

    private VerticalLayout buildRoleEditor(String username, HAccount account) {
        List<String> allRoles = userQuery.allRoleNames();
        Set<String> current = account.getRoles().stream()
                .map(HAccountRole::getName)
                .collect(Collectors.toCollection(HashSet::new));

        CheckboxGroup<String> roles = new CheckboxGroup<>();
        roles.setLabel(getTranslation("adminUser.roles"));
        roles.setItems(allRoles);
        roles.setValue(new HashSet<>(current));

        Button save = new Button(getTranslation("adminUser.save"), e -> {
            boolean ok = userQuery.updateRoles(username, roles.getValue());
            Notification.show(getTranslation(ok
                    ? "adminUser.rolesSaved" : "adminUser.notFound"));
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout editor = new VerticalLayout(roles, save);
        editor.setPadding(false);
        editor.setSpacing(true);
        return editor;
    }
}
