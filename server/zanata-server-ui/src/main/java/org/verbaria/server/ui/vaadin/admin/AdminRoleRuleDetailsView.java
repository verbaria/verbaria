package org.verbaria.server.ui.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/roleruledetails", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminRoleRuleDetailsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.roleRuleDetails"; }


    public AdminRoleRuleDetailsView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminRoleRuleDetails.heading")));

        FormLayout form = new FormLayout();
        TextField policyName = new TextField(getTranslation("adminRoleRuleDetails.policyName"));
        TextField identityPattern = new TextField(getTranslation("adminRoleRuleDetails.identityPattern"));
        TextField role = new TextField(getTranslation("adminRoleRuleDetails.role"));
        form.add(policyName, identityPattern, role);

        Button save = new Button(getTranslation("adminRoleRuleDetails.save"));
        save.setEnabled(false);

        add(form, save);
    }
}
