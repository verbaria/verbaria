package org.verbaria.server.ui.vaadin.admin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.security.core.context.SecurityContextHolder;

import org.zanata.model.HAccount;
import org.zanata.model.LanguageRequest;
import org.zanata.model.type.RequestState;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.headless.repository.AccountRepository;
import org.verbaria.server.headless.repository.LanguageRequestRepository;
import org.verbaria.server.headless.service.LanguageTeamService;
import org.verbaria.server.ui.vaadin.MainLayout;

@Route(value = "admin/requests", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminRequestsView extends VerticalLayout implements TitleKey {

    private final LanguageRequestRepository requestRepository;
    private final LanguageTeamService teamService;
    private final AccountRepository accountRepository;

    private final Grid<LanguageRequest> grid = new Grid<>(LanguageRequest.class, false);

    public AdminRequestsView(LanguageRequestRepository requestRepository,
                             LanguageTeamService teamService,
                             AccountRepository accountRepository) {
        this.requestRepository = requestRepository;
        this.teamService = teamService;
        this.accountRepository = accountRepository;

        setSizeFull();
        setPadding(true);

        add(new H2(getTranslation("page.requests")));

        grid.addColumn(this::requesterName)
                .setHeader(getTranslation("admin.requests.col.requester"))
                .setAutoWidth(true);
        grid.addColumn(r -> r.getLocale().getLocaleId().getId())
                .setHeader(getTranslation("admin.requests.col.locale"))
                .setAutoWidth(true);
        grid.addColumn(r -> r.getLocale().getDisplayName())
                .setHeader(getTranslation("admin.requests.col.localeName"))
                .setAutoWidth(true);
        grid.addColumn(AdminRequestsView::describeRoles)
                .setHeader(getTranslation("admin.requests.col.roles"))
                .setAutoWidth(true);
        grid.addColumn(r -> r.getRequest().getValidFrom())
                .setHeader(getTranslation("admin.requests.col.requestedAt"))
                .setAutoWidth(true);
        grid.addComponentColumn(this::actions)
                .setHeader(getTranslation("admin.requests.col.actions"))
                .setAutoWidth(true);

        grid.setAllRowsVisible(true);
        add(grid);

        refresh();
    }

    @Override public String pageTitleKey() { return "page.requests"; }

    private void refresh() {
        List<LanguageRequest> open = new ArrayList<>(
                requestRepository.findAllByState(RequestState.NEW));
        if (open.isEmpty()) {
            grid.setVisible(false);
            add(new Paragraph(getTranslation("admin.requests.empty")));
        } else {
            grid.setVisible(true);
            grid.setItems(open);
        }
    }

    private HorizontalLayout actions(LanguageRequest request) {
        Button accept = new Button(getTranslation("language.team.accept"), e -> {
            currentAccount().ifPresent(actor -> {
                teamService.approve(request, actor, "");
                Notification.show(getTranslation("language.team.acceptedToast"),
                        2000, Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            });
        });
        accept.addThemeVariants(ButtonVariant.SUCCESS, ButtonVariant.SMALL);

        Button decline = new Button(getTranslation("language.team.decline"),
                e -> openDeclineDialog(request));
        decline.addThemeVariants(ButtonVariant.ERROR, ButtonVariant.SMALL);

        HorizontalLayout row = new HorizontalLayout(accept, decline);
        row.setSpacing(true);
        return row;
    }

    private void openDeclineDialog(LanguageRequest request) {
        Dialog dlg = new Dialog();
        dlg.setHeaderTitle(getTranslation("admin.requests.decline.title"));
        TextArea reason = new TextArea(getTranslation("admin.requests.decline.reasonLabel"));
        reason.setWidthFull();
        dlg.add(reason);
        Button confirm = new Button(getTranslation("language.team.decline"), e -> {
            currentAccount().ifPresent(actor -> {
                teamService.decline(request, actor, reason.getValue());
                Notification.show(getTranslation("admin.requests.declinedToast"),
                        2000, Notification.Position.BOTTOM_START);
                dlg.close();
                UI.getCurrent().getPage().reload();
            });
        });
        confirm.addThemeVariants(ButtonVariant.ERROR);
        Button cancel = new Button(getTranslation("common.cancel"), e -> dlg.close());
        dlg.getFooter().add(cancel, confirm);
        dlg.open();
    }

    private String requesterName(LanguageRequest r) {
        var requester = r.getRequest().getRequester();
        if (requester == null) return "—";
        var person = requester.getPerson();
        return (person != null && person.getName() != null)
                ? person.getName()
                : requester.getUsername();
    }

    private static String describeRoles(LanguageRequest r) {
        List<String> roles = new ArrayList<>();
        if (r.isCoordinator()) roles.add("Coordinator");
        if (r.isReviewer())    roles.add("Reviewer");
        if (r.isTranslator())  roles.add("Translator");
        return roles.isEmpty() ? "—" : String.join(", ", roles);
    }

    private Optional<HAccount> currentAccount() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return Optional.empty();
        return accountRepository.findByUsername(auth.getName());
    }
}
