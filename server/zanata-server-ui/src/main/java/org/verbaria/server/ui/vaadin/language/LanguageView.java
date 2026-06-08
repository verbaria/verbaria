package org.verbaria.server.ui.vaadin.language;

import org.verbaria.server.headless.security.Roles;

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
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

import de.f0rce.ace.AceEditor;
import de.f0rce.ace.enums.AceMode;
import de.f0rce.ace.enums.AceTheme;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.common.LocaleId;
import org.zanata.model.HAccount;
import org.zanata.model.HLocale;
import org.zanata.model.HLocaleMember;
import org.zanata.model.LanguageRequest;
import org.zanata.model.type.RequestState;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LanguageRequestRepository;
import org.verbaria.server.headless.repository.LocaleMemberRepository;
import org.verbaria.server.headless.repository.LocaleRepository;
import org.verbaria.server.headless.service.LanguageTeamService;
import org.verbaria.server.headless.service.ai.AiPolicyService;
import org.verbaria.server.headless.service.ai.TranslationProviderRegistry;
import org.verbaria.server.ui.vaadin.BreadcrumbsService;
import org.verbaria.server.ui.vaadin.HasBreadcrumbs;
import org.verbaria.server.ui.vaadin.ProgressDialogService;
import org.verbaria.server.ui.vaadin.MainLayout;

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
    private final TranslationProviderRegistry providerRegistry;
    private final AiPolicyService aiPolicy;
    private final ProgressDialogService progressDialogs;

    private final VerticalLayout body = new VerticalLayout();

    public LanguageView(LocaleRepository localeRepository,
                        LocaleMemberRepository localeMemberRepository,
                        LanguageRequestRepository languageRequestRepository,
                        AccountRepository accountRepository,
                        LanguageTeamService languageTeamService,
                        BreadcrumbsService breadcrumbsService,
                        TranslationProviderRegistry providerRegistry,
                        AiPolicyService aiPolicy,
                        ProgressDialogService progressDialogs) {
        this.localeRepository = localeRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.languageRequestRepository = languageRequestRepository;
        this.accountRepository = accountRepository;
        this.languageTeamService = languageTeamService;
        this.breadcrumbsService = breadcrumbsService;
        this.providerRegistry = providerRegistry;
        this.aiPolicy = aiPolicy;
        this.progressDialogs = progressDialogs;
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        body.setPadding(true);
        body.setSpacing(true);
        body.setWidthFull();
        Scroller scroller = new Scroller(body);
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.setSizeFull();
        addAndExpand(scroller);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        body.removeAll();
        String slug = event.getRouteParameters().get("slug").orElse("");
        HLocale locale = localeRepository.findByLocaleId(new LocaleId(slug))
                .orElseThrow(() -> new NotFoundException("Language not found: " + slug));

        publishBreadcrumb(slug);
        body.add(buildHeader(locale));

        Optional<HAccount> currentOpt = currentAccount();
        boolean canEditPrompt = Roles.isCurrentUserAdmin()
                || (currentOpt.isPresent()
                        && languageTeamService.membership(locale, currentOpt.get().getPerson())
                                .map(HLocaleMember::isReviewer).orElse(false));
        boolean canManage = currentOpt.isPresent() && languageTeamService.canManage(
                locale, currentOpt.get().getPerson(), Roles.isCurrentUserAdmin());

        VerticalLayout membersTab = new VerticalLayout();
        membersTab.setPadding(false);
        membersTab.setSpacing(true);
        membersTab.setWidthFull();
        membersTab.add(buildMembersPanel(locale));
        if (canManage) {
            membersTab.add(buildPendingRequestsPanel(locale, currentOpt.get()));
        }

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(new Tab(getTranslation("language.tab.members")), membersTab);
        tabs.add(new Tab(getTranslation("language.tab.aiPrompt")),
                buildAiPromptPanel(locale, canEditPrompt));
        body.add(tabs);
    }

    private Div buildAiPromptPanel(HLocale locale, boolean canEdit) {
        Div panel = new Div();
        panel.setWidthFull();
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER);
        H3 title = new H3(getTranslation("language.ai.title"));
        title.addClassNames(AuraUtility.Margin.NONE);
        panel.add(title);
        Paragraph hint = new Paragraph(getTranslation("language.ai.hint"));
        hint.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL,
                AuraUtility.Margin.Top.NONE);
        panel.add(hint);

        AceEditor editor = new AceEditor();
        editor.setMode(AceMode.text);
        editor.setTheme(AceTheme.textmate);
        editor.setValue(locale.getAiPrompt() == null ? "" : locale.getAiPrompt());
        editor.setWidthFull();
        editor.setHeight("160px");
        editor.setReadOnly(!canEdit);
        editor.setShowPrintMargin(false);
        panel.add(editor);

        if (canEdit) {
            Button save = new Button(getTranslation("language.ai.save"), e -> {
                String value = editor.getValue();
                localeRepository.findByLocaleId(locale.getLocaleId()).ifPresent(l -> {
                    l.setAiPrompt(value == null || value.isBlank() ? null : value);
                    localeRepository.save(l);
                });
                Notification.show(getTranslation("language.ai.saved"),
                        2000, Notification.Position.BOTTOM_START);
            });
            save.addThemeVariants(ButtonVariant.PRIMARY);
            HorizontalLayout actions = new HorizontalLayout(save);
            actions.addClassNames(AuraUtility.Margin.Top.SMALL);
            panel.add(actions);

            H3 evalTitle = new H3(getTranslation("language.ai.evaluate"));
            evalTitle.addClassNames(AuraUtility.FontSize.MEDIUM,
                    AuraUtility.Margin.Top.MEDIUM, AuraUtility.Margin.Bottom.SMALL);
            Paragraph evalHint = new Paragraph(getTranslation("language.ai.evaluateHint"));
            evalHint.addClassNames(AuraUtility.TextColor.SECONDARY,
                    AuraUtility.FontSize.SMALL, AuraUtility.Margin.NONE);
            TextField keyField = new TextField(getTranslation("translate.key"));
            keyField.setWidthFull();
            keyField.setPlaceholder("test.key");
            TextArea valueField = new TextArea(getTranslation("translate.value"));
            valueField.setWidthFull();
            valueField.setMinHeight("4rem");
            Div result = new Div();
            result.setWidthFull();
            result.addClassNames(AuraUtility.Whitespace.PRE_WRAP, AuraUtility.Border.ALL,
                    AuraUtility.BorderColor.SECONDARY, AuraUtility.BorderRadius.MEDIUM,
                    AuraUtility.Padding.SMALL, AuraUtility.Background.BASE,
                    AuraUtility.Margin.Top.SMALL, AuraUtility.TextColor.SECONDARY);
            result.setText(getTranslation("language.ai.evaluatePlaceholder"));
            Button evaluate = new Button(getTranslation("language.ai.evaluateRun"), e -> {
                if (!aiPolicy.canCurrentUserUseAi()) {
                    Notification.show(getTranslation("language.ai.noAi"),
                            3000, Notification.Position.MIDDLE);
                    return;
                }
                var providers = providerRegistry.available();
                if (providers.isEmpty()) {
                    Notification.show(getTranslation("language.ai.noProvider"),
                            3000, Notification.Position.MIDDLE);
                    return;
                }
                String src = valueField.getValue();
                if (src == null || src.isBlank()) {
                    valueField.setInvalid(true);
                    return;
                }
                valueField.setInvalid(false);
                var provider = providers.get(0);
                LocaleId target = locale.getLocaleId();
                String guidance = editor.getValue();
                String g = guidance == null || guidance.isBlank() ? null : guidance;
                String ctx = keyField.getValue() == null || keyField.getValue().isBlank()
                        ? keyField.getPlaceholder() : keyField.getValue();
                progressDialogs.run(getTranslation("language.ai.evaluate"), handle -> {
                    handle.status(handle.t("language.ai.evaluateRunning",
                            provider.displayName()));
                    return provider.translate(src, LocaleId.EN_US, target, ctx, g);
                }).whenComplete((out, err) -> {
                    if (err != null) {
                        result.setText(getTranslation("common.failed", err.getMessage()));
                        return;
                    }
                    result.removeClassName(AuraUtility.TextColor.SECONDARY);
                    result.setText(out == null || out.isBlank()
                            ? getTranslation("language.ai.evaluateEmpty") : out);
                });
            });
            evaluate.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            panel.add(evalTitle, evalHint, keyField, valueField, evaluate, result);
        }
        return panel;
    }

    private void publishBreadcrumb(String slug) {
        breadcrumbsService.set(
                BreadcrumbsService.Crumb.of(getTranslation("translate.breadcrumb.home"), "/"),
                BreadcrumbsService.Crumb.of(getTranslation("nav.languages"), "/languages"),
                BreadcrumbsService.Crumb.here(slug));
    }

    private HorizontalLayout buildHeader(HLocale locale) {
        Span name = new Span(locale.getDisplayName() == null
                || locale.getDisplayName().isBlank()
                ? locale.getLocaleId().getId() : locale.getDisplayName());
        name.addClassNames(AuraUtility.FontWeight.SEMIBOLD, AuraUtility.FontSize.LARGE);
        HorizontalLayout info = new HorizontalLayout(name);
        info.setAlignItems(FlexComponent.Alignment.BASELINE);
        info.setSpacing(true);
        if (locale.getNativeName() != null && !locale.getNativeName().isBlank()) {
            Span nativeName = new Span(locale.getNativeName());
            nativeName.addClassNames(AuraUtility.TextColor.SECONDARY);
            info.add(nativeName);
        }
        Span code = new Span(locale.getLocaleId().getId());
        code.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY);
        info.add(code);

        HorizontalLayout row = new HorizontalLayout(info, buildActionBar(locale));
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        return row;
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
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER);
        H3 title = new H3(getTranslation("language.team.members"));
        title.addClassNames(AuraUtility.Margin.NONE);
        panel.add(title);

        List<HLocaleMember> members = localeMemberRepository.findByLocale(locale);
        Span counter = new Span(members.size() + " member" + (members.size() == 1 ? "" : "s"));
        counter.addClassNames(AuraUtility.FontSize.SMALL, AuraUtility.TextColor.SECONDARY);
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
        panel.addClassNames(AuraUtility.Border.ALL, AuraUtility.BorderColor.SECONDARY,
                AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.MEDIUM,
                AuraUtility.BoxSizing.BORDER);
        H3 title = new H3(getTranslation("language.team.pendingTitle"));
        title.addClassNames(AuraUtility.Margin.NONE);
        panel.add(title);

        List<LanguageRequest> requests = new ArrayList<>(
                languageRequestRepository.findByLocaleAndState(locale, RequestState.NEW));
        if (requests.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("language.team.noPending"));
            empty.addClassNames(AuraUtility.TextColor.SECONDARY);
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
