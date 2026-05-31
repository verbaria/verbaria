package org.verbaria.server.ui.vaadin.group;

import jakarta.annotation.security.PermitAll;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;

import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.model.HIterationGroup;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.IterationGroupRepository;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "group/create", layout = MainLayout.class)
@PermitAll
public class GroupCreateView extends VerticalLayout implements TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.newGroup"; }


    public GroupCreateView(IterationGroupRepository groupRepository,
                           AccountRepository accountRepository,
                           BreadcrumbsService breadcrumbsService) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("nav.groups"), "/groups"),
                BreadcrumbsService.Crumb.here(getTranslation("groupCreate.crumb")));
        add(new H2(getTranslation("groupCreate.heading")));

        TextField slug = new TextField(getTranslation("groupCreate.groupId"));
        slug.setRequiredIndicatorVisible(true);
        TextField name = new TextField(getTranslation("groupCreate.name"));
        name.setRequiredIndicatorVisible(true);
        TextArea description = new TextArea(getTranslation("groupCreate.description"));
        description.setMaxLength(255);
        FormLayout form = new FormLayout(slug, name, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        add(form);

        Button create = new Button(getTranslation("groupCreate.submitBtn"), e -> {
            if (slug.getValue() == null || slug.getValue().isBlank()
                    || name.getValue() == null || name.getValue().isBlank()) {
                Notification.show(getTranslation("groupCreate.requiredFields"), 3000,
                        Notification.Position.MIDDLE);
                return;
            }
            String s = slug.getValue().trim();
            if (groupRepository.findBySlug(s).isPresent()) {
                Notification.show(getTranslation("groupCreate.duplicate", s),
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
            Notification.show(getTranslation("groupCreate.success", s), 2500,
                    Notification.Position.BOTTOM_START);
            getUI().ifPresent(ui -> ui.navigate("group/view/" + s));
        });
        create.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"),
                e -> getUI().ifPresent(ui -> ui.navigate("groups")));
        add(new HorizontalLayout(create, cancel));
    }
}
