package org.zanata.spring.vaadin.group;

import jakarta.annotation.security.PermitAll;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.model.HIterationGroup;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "group/create", layout = MainLayout.class)
@PageTitle("New group | Zanata")
@PermitAll
public class GroupCreateView extends VerticalLayout {

    public GroupCreateView(IterationGroupRepository groupRepository,
                           AccountRepository accountRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Groups", "/groups"),
                new Breadcrumb("New group", "#", true));
        add(crumbs);
        add(new H2("New version group"));

        TextField slug = new TextField("Group ID");
        slug.setRequiredIndicatorVisible(true);
        TextField name = new TextField("Name");
        name.setRequiredIndicatorVisible(true);
        TextArea description = new TextArea("Description");
        description.setMaxLength(255);
        FormLayout form = new FormLayout(slug, name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        Button create = new Button("Create", e -> {
            if (slug.getValue() == null || slug.getValue().isBlank()
                    || name.getValue() == null || name.getValue().isBlank()) {
                Notification.show("Group ID and Name are required.", 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            String s = slug.getValue().trim();
            if (groupRepository.findBySlug(s).isPresent()) {
                Notification.show("A group with id '" + s + "' already exists.",
                        3000, Notification.Position.MIDDLE);
                return;
            }
            HIterationGroup g = new HIterationGroup();
            g.setSlug(s);
            g.setName(name.getValue().trim());
            g.setDescription(description.getValue());
            String username = SecurityContextHolder.getContext().getAuthentication() == null
                    ? null : SecurityContextHolder.getContext().getAuthentication().getName();
            if (username != null) {
                accountRepository.findByUsername(username)
                        .map(a -> a.getPerson())
                        .ifPresent(person -> g.getMaintainers().add(person));
            }
            groupRepository.save(g);
            Notification.show("Created group " + s, 2500,
                    Notification.Position.BOTTOM_START);
            getUI().ifPresent(ui -> ui.navigate("group/view/" + s));
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button("Cancel",
                e -> getUI().ifPresent(ui -> ui.navigate("groups")));
        add(new HorizontalLayout(create, cancel));
    }
}
