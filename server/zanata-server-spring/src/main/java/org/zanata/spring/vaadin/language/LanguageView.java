package org.zanata.spring.vaadin.language;

import org.zanata.spring.security.Roles;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HLocaleMember;
import org.zanata.model.LanguageRequest;
import org.zanata.model.type.RequestState;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.LanguageRequestRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.service.LanguageTeamService;
import org.zanata.spring.vaadin.BreadcrumbsService;
import org.zanata.spring.vaadin.HasBreadcrumbs;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "language/:slug", layout = MainLayout.class)
@AnonymousAllowed
public class LanguageView extends VerticalLayout implements BeforeEnterObserver, TitleKey, HasBreadcrumbs {

    @Override public String pageTitleKey() { return "page.language"; }


    private final LocaleRepository localeRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final LanguageRequestRepository languageRequestRepository;
    private final AccountRepository accountRepository;
    private final LanguageTeamService languageTeamService;
    private final BreadcrumbsService breadcrumbsService;

    public LanguageView(LocaleRepository localeRepository,
                        LocaleMemberRepository localeMemberRepository,
                        LanguageRequestRepository languageRequestRepository,
                        AccountRepository accountRepository,
                        LanguageTeamService languageTeamService,
                        BreadcrumbsService breadcrumbsService) {
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.languageRequestRepository = languageRequestRepository;
        this.accountRepository = accountRepository;
        this.languageTeamService = languageTeamService;
        this.breadcrumbsService = breadcrumbsService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HLocale locale = localeRepository.findByLocaleId(new LocaleId(slug))
                .orElseThrow(() -> new NotFoundException("Language not found: " + slug));

        publishBreadcrumb(slug);
        add(buildHeader(locale));
        add(buildActionBar(locale));
        add(buildMembersPanel(locale));

        Optional<HAccount> currentOpt = currentAccount();
        boolean canManage = currentOpt.isPresent() && languageTeamService.canManage(
                locale, currentOpt.get().getPerson(), Roles.isCurrentUserAdmin());
        if (canManage) {
            add(buildPendingRequestsPanel(locale, currentOpt.get()));
        }
    }

    private void publishBreadcrumb(String slug) {
        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("nav.languages"), "/languages"),
                BreadcrumbsService.Crumb.here(slug));
    }

    private Div buildHeader(HLocale locale) {
        Div panel = new Div();
        panel.setWidthFull();
        H1 name = new H1(locale.getDisplayName() == null
                ? locale.getLocaleId().getId() : locale.getDisplayName());
        name.addClassNames(LumoUtility.Margin.NONE);
        panel.add(name);
        if (locale.getNativeName() != null && !locale.getNativeName().isBlank()) {
            Paragraph nativeName = new Paragraph(locale.getNativeName());
            nativeName.addClassNames(LumoUtility.Margin.NONE, LumoUtility.TextColor.SECONDARY);
            panel.add(nativeName);
        }
        Paragraph code = new Paragraph(locale.getLocaleId().getId());
        code.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.SMALL,
                LumoUtility.TextColor.SECONDARY);
        panel.add(code);
        return panel;
    }

    private HorizontalLayout buildActionBar(HLocale locale) {
        HorizontalLayout bar = new HorizontalLayout();
        bar.setSpacing(true);
        bar.setAlignItems(FlexComponent.Alignment.CENTER);

        Optional<HAccount> currentOpt = currentAccount();
        if (currentOpt.isEmpty()) {
            Button signIn = new Button(getTranslation("language.team.signInToJoin"),
                    e -> UI.getCurrent().getPage().setLocation("/login"));
            signIn.addThemeVariants(ButtonVariant.PRIMARY);
            bar.add(signIn);
            return bar;
        }
        HAccount current = currentOpt.get();
        Optional<HLocaleMember> existing = languageTeamService.membership(locale, current.getPerson());
        if (existing.isPresent()) {
            Span badge = new Span(getTranslation("language.team.memberPrefix",
                    describeRoles(existing.get())));
            badge.getElement().getThemeList().add("badge success small");
            Button leave = new Button(getTranslation("language.team.leave"), e -> {
                languageTeamService.leave(locale, current.getPerson());
                Notification.show(getTranslation("language.team.leftToast"),
                        2000, Notification.Position.BOTTOM_START);
                refresh();
            });
            leave.addThemeVariants(ButtonVariant.TERTIARY);
            bar.add(badge, leave);
            return bar;
        }
        Optional<LanguageRequest> pending = languageTeamService.pendingRequestFor(locale, current);
        if (pending.isPresent()) {
            Span badge = new Span(getTranslation("language.team.requestPending",
                    describeRequestedRoles(pending.get())));
            badge.getElement().getThemeList().add("badge contrast small");
            Button cancel = new Button(getTranslation("language.team.cancel"), e -> {
                languageTeamService.cancelOwnRequest(pending.get(), current);
                Notification.show(getTranslation("language.team.requestCancelledToast"),
                        2000, Notification.Position.BOTTOM_START);
                refresh();
            });
            cancel.addThemeVariants(ButtonVariant.TERTIARY);
            bar.add(badge, cancel);
            return bar;
        }
        Button join = new Button(getTranslation("language.team.join"),
                e -> openJoinDialog(locale, current));
        join.addThemeVariants(ButtonVariant.PRIMARY);
        bar.add(join);
        return bar;
    }

    private void openJoinDialog(HLocale locale, HAccount current) {
        Dialog d = new Dialog();
        d.setHeaderTitle(getTranslation("language.team.joinDialogTitle",
                locale.getDisplayName() == null ? locale.getLocaleId().getId() : locale.getDisplayName()));
        Checkbox translator = new Checkbox(getTranslation("language.team.role.translator"), true);
        Checkbox reviewer = new Checkbox(getTranslation("language.team.role.reviewer"));
        Checkbox coordinator = new Checkbox(getTranslation("language.team.role.coordinator"));
        TextArea note = new TextArea(getTranslation("language.team.contactCoordinators"));
        note.setWidthFull();
        VerticalLayout body = new VerticalLayout(translator, reviewer, coordinator, note);
        body.setPadding(false);
        d.add(body);

        Button send = new Button(getTranslation("language.team.send"), e -> {
            try {
                languageTeamService.requestJoin(current, locale,
                        translator.getValue(), reviewer.getValue(),
                        coordinator.getValue(), note.getValue());
                Notification.show(getTranslation("language.team.requestSentToast"),
                        2000, Notification.Position.BOTTOM_START);
                d.close();
                refresh();
            } catch (Exception ex) {
                Notification.show(ex.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });
        send.addThemeVariants(ButtonVariant.PRIMARY);
        Button cancel = new Button(getTranslation("common.cancel"), e -> d.close());
        d.getFooter().add(cancel, send);
        d.open();
    }

    private Div buildMembersPanel(HLocale locale) {
        Div panel = new Div();
        panel.setWidthFull();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        H3 title = new H3(getTranslation("language.team.members"));
        title.addClassNames(LumoUtility.Margin.NONE);
        panel.add(title);

        List<HLocaleMember> members = localeMemberRepository.findByLocale(locale);
        Span counter = new Span(members.size() + " member" + (members.size() == 1 ? "" : "s"));
        counter.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        panel.add(counter);

        Grid<HLocaleMember> grid = new Grid<>(HLocaleMember.class, false);
        grid.addColumn(m -> {
                    var p = m.getPerson();
                    return p == null || p.getName() == null ? "" : p.getName();
                }).setHeader(getTranslation("language.team.col.name")).setAutoWidth(true);
        grid.addColumn(m -> {
                    var p = m.getPerson();
                    return p == null || p.getAccount() == null ? "" : p.getAccount().getUsername();
                }).setHeader(getTranslation("language.team.col.username")).setAutoWidth(true);
        grid.addColumn(this::describeRoles)
                .setHeader(getTranslation("language.team.col.roles")).setAutoWidth(true);
        grid.setItems(members);
        grid.setAllRowsVisible(true);
        panel.add(grid);
        return panel;
    }

    private Div buildPendingRequestsPanel(HLocale locale, HAccount currentAccount) {
        Div panel = new Div();
        panel.setWidthFull();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        H3 title = new H3(getTranslation("language.team.pendingTitle"));
        title.addClassNames(LumoUtility.Margin.NONE);
        panel.add(title);

        List<LanguageRequest> requests = new ArrayList<>(
                languageRequestRepository.findByLocaleAndState(locale, RequestState.NEW));
        if (requests.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("language.team.noPending"));
            empty.addClassNames(LumoUtility.TextColor.SECONDARY);
            panel.add(empty);
            return panel;
        }
        Grid<LanguageRequest> grid = new Grid<>(LanguageRequest.class, false);
        grid.addColumn(r -> {
                    var p = r.getRequest().getRequester().getPerson();
                    return p == null || p.getName() == null
                            ? r.getRequest().getRequester().getUsername()
                            : p.getName();
                }).setHeader(getTranslation("language.team.col.requester")).setAutoWidth(true);
        grid.addColumn(this::describeRequestedRoles)
                .setHeader(getTranslation("language.team.col.requestedRoles")).setAutoWidth(true);
        grid.addComponentColumn(r -> {
            Button accept = new Button(getTranslation("language.team.accept"), e -> {
                languageTeamService.approve(r, currentAccount, "");
                Notification.show(getTranslation("language.team.acceptedToast"),
                        2000, Notification.Position.BOTTOM_START);
                refresh();
            });
            accept.addThemeVariants(ButtonVariant.SUCCESS, ButtonVariant.SMALL);
            Button decline = new Button(getTranslation("language.team.decline"),
                    e -> openDeclineDialog(r, currentAccount));
            decline.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);
            HorizontalLayout actions = new HorizontalLayout(accept, decline);
            actions.setSpacing(true);
            return actions;
        }).setHeader(getTranslation("language.team.col.actions")).setAutoWidth(true);
        grid.setItems(requests);
        grid.setAllRowsVisible(true);
        panel.add(grid);
        return panel;
    }

    private void openDeclineDialog(LanguageRequest request, HAccount actor) {
        Dialog d = new Dialog();
        d.setHeaderTitle("Decline request");
        TextArea reason = new TextArea("Reason (optional)");
        reason.setWidthFull();
        d.add(reason);
        Button confirm = new Button("Decline", e -> {
            languageTeamService.decline(request, actor, reason.getValue());
            Notification.show("Declined", 2000, Notification.Position.BOTTOM_START);
            d.close();
            refresh();
        });
        confirm.addThemeVariants(ButtonVariant.ERROR);
        Button cancel = new Button("Cancel", e -> d.close());
        d.getFooter().add(cancel, confirm);
        d.open();
    }

    private String describeRoles(HLocaleMember m) {
        List<String> roles = new ArrayList<>();
        if (m.isCoordinator()) roles.add("Coordinator");
        if (m.isReviewer())    roles.add("Reviewer");
        if (m.isTranslator())  roles.add("Translator");
        return roles.isEmpty() ? "—" : String.join(", ", roles);
    }

    private String describeRequestedRoles(LanguageRequest r) {
        List<String> roles = new ArrayList<>();
        if (r.isCoordinator()) roles.add("Coordinator");
        if (r.isReviewer())    roles.add("Reviewer");
        if (r.isTranslator())  roles.add("Translator");
        return roles.isEmpty() ? "—" : String.join(", ", roles);
    }

    private void refresh() {
        UI.getCurrent().getPage().reload();
    }

    private Optional<HAccount> currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return accountRepository.findByUsername(auth.getName());
    }

}
