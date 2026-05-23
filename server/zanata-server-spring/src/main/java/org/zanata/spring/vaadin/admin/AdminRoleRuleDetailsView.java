package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/roleruledetails", layout = MainLayout.class)
@PageTitle("Role Rule Details | Zanata")
@RolesAllowed("ADMIN")
public class AdminRoleRuleDetailsView extends VerticalLayout {

    public AdminRoleRuleDetailsView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Role Rule Details"));

        FormLayout form = new FormLayout();
        TextField policyName = new TextField("Policy name");
        TextField identityPattern = new TextField("Identity pattern");
        TextField role = new TextField("Role");
        form.add(policyName, identityPattern, role);

        Button save = new Button("Save");
        save.setEnabled(false);

        add(form, save);
    }
}
