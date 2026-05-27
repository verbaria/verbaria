package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.model.HAccount;
import org.zanata.model.HLocaleMember;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.service.UserSettingsService;
import org.zanata.spring.service.ai.AiPolicyService;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard/settings", layout = MainLayout.class)
@PermitAll
public class DashboardSettingsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.settings"; }


    private final AccountRepository accountRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final UserSettingsService userSettingsService;
    private final AiPolicyService aiPolicy;

    public DashboardSettingsView(AccountRepository accountRepository,
                                 LocaleMemberRepository localeMemberRepository,
                                 UserSettingsService userSettingsService,
                                 AiPolicyService aiPolicy) {
        this.accountRepository = accountRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.userSettingsService = userSettingsService;
        this.aiPolicy = aiPolicy;

        setSizeFull();
        setPadding(true);
        getStyle().set("max-width", "1000px");
        getStyle().set("margin", "0 auto");

        H2 heading = new H2(getTranslation("dashboardSettings.title"));

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(getTranslation("dashboardSettings.tab.account"), buildAccountTab());
        tabs.add(getTranslation("dashboardSettings.tab.profile"), buildProfileTab());
        tabs.add(getTranslation("dashboardSettings.tab.languages"), buildLanguagesTab());
        tabs.add(getTranslation("dashboardSettings.tab.client"), buildClientTab());

        add(heading, tabs);
    }

    /* ---------------- Account: email + password ---------------- */

    private Div buildAccountTab() {
        Div card = card();
        HAccount account = currentAccount().orElse(null);
        if (account == null) {
            card.add(notSignedIn());
            return card;
        }

        TextField email = new TextField(getTranslation("dashboardSettings.account.email"));
        if (account.getPerson() != null && account.getPerson().getEmail() != null) {
            email.setValue(account.getPerson().getEmail());
        }
        email.setWidth("360px");

        Button updateEmail = new Button(getTranslation("dashboardSettings.account.updateEmail"),
                LineAwesomeIcon.SAVE_SOLID.create(),
                e -> {
                    userSettingsService.updateProfile(account.getUsername(), null, email.getValue());
                    toast(getTranslation("dashboardSettings.account.emailSaved"), false);
                });
        updateEmail.addThemeVariants(ButtonVariant.PRIMARY);

        PasswordField oldPw = new PasswordField(getTranslation("dashboardSettings.account.currentPassword"));
        PasswordField newPw = new PasswordField(getTranslation("dashboardSettings.account.newPassword"));
        PasswordField confirmPw = new PasswordField(getTranslation("dashboardSettings.account.confirmPassword"));
        Button changePw = new Button(getTranslation("dashboardSettings.account.changePassword"),
                LineAwesomeIcon.KEY_SOLID.create(), e -> {
            if (!newPw.getValue().equals(confirmPw.getValue())) {
                toast(getTranslation("dashboardSettings.account.passwordMismatch"), true);
                return;
            }
            if (newPw.getValue().length() < 6) {
                toast(getTranslation("dashboardSettings.account.passwordTooShort"), true);
                return;
            }
            boolean ok = userSettingsService.changePassword(account.getUsername(),
                    oldPw.getValue(), newPw.getValue());
            if (ok) {
                oldPw.clear();
                newPw.clear();
                confirmPw.clear();
                toast(getTranslation("dashboardSettings.account.passwordChanged"), false);
            } else {
                toast(getTranslation("dashboardSettings.account.passwordIncorrect"), true);
            }
        });

        FormLayout pwForm = new FormLayout(oldPw, newPw, confirmPw);
        pwForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3));

        card.add(sectionTitle(getTranslation("dashboardSettings.account.emailSection")));
        card.add(new HorizontalLayout(email, updateEmail) {{
            setAlignItems(FlexComponent.Alignment.END);
            setSpacing(true);
        }});
        card.add(spacer());
        card.add(sectionTitle(getTranslation("dashboardSettings.account.changePasswordSection")));
        card.add(pwForm, changePw);
        return card;
    }

    /* ---------------- Profile: display name ---------------- */

    private Div buildProfileTab() {
        Div card = card();
        HAccount account = currentAccount().orElse(null);
        if (account == null) {
            card.add(notSignedIn());
            return card;
        }
        TextField username = new TextField(getTranslation("dashboardSettings.profile.username"));
        username.setReadOnly(true);
        username.setValue(account.getUsername() == null ? "" : account.getUsername());

        TextField displayName = new TextField(getTranslation("dashboardSettings.profile.displayName"));
        if (account.getPerson() != null && account.getPerson().getName() != null) {
            displayName.setValue(account.getPerson().getName());
        }

        Button save = new Button(getTranslation("dashboardSettings.profile.saveProfile"),
                LineAwesomeIcon.SAVE_SOLID.create(), e -> {
            userSettingsService.updateProfile(account.getUsername(), displayName.getValue(), null);
            toast(getTranslation("dashboardSettings.profile.profileSaved"), false);
        });
        save.addThemeVariants(ButtonVariant.PRIMARY);

        FormLayout form = new FormLayout(username, displayName);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        card.add(sectionTitle(getTranslation("dashboardSettings.profile.publicProfile")), form, save);

        // --- per-user "allow AI translation" toggle ---
        // Hidden entirely when the admin hasn't enabled the AI feature.
        if (aiPolicy.isGloballyEnabled()) {
            card.add(spacer());
            card.add(sectionTitle(getTranslation("dashboardSettings.profile.aiSection")));
            Checkbox allowAi = new Checkbox(getTranslation("dashboardSettings.profile.aiToggle"));
            allowAi.setValue(aiPolicy.isAllowedForUser(account.getUsername()));
            allowAi.addValueChangeListener(e -> {
                aiPolicy.setAllowedForUser(account.getUsername(),
                        Boolean.TRUE.equals(e.getValue()));
                toast(getTranslation("dashboardSettings.profile.aiSaved"), false);
            });
            Paragraph aiHint = new Paragraph(getTranslation("dashboardSettings.profile.aiHint"));
            aiHint.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            aiHint.getStyle().set("font-size", "0.85rem");
            aiHint.getStyle().set("margin", "0.25rem 0 0 0");
            card.add(allowAi, aiHint);
        }
        return card;
    }

    /* ---------------- Languages: list user's team memberships ---------------- */

    private Div buildLanguagesTab() {
        Div card = card();
        HAccount account = currentAccount().orElse(null);
        if (account == null) {
            card.add(notSignedIn());
            return card;
        }
        if (account.getPerson() == null) {
            card.add(new Paragraph(getTranslation("dashboardSettings.languages.noPerson")));
            return card;
        }
        List<HLocaleMember> memberships =
                localeMemberRepository.findByPerson(account.getPerson());
        card.add(sectionTitle(getTranslation("dashboardSettings.languages.yourTeams")));
        if (memberships.isEmpty()) {
            Paragraph p = new Paragraph(getTranslation("dashboardSettings.languages.notInTeams"));
            p.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            Anchor browse = new Anchor("/languages",
                    getTranslation("dashboardSettings.languages.browse"));
            card.add(p, browse);
            return card;
        }
        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        for (HLocaleMember m : memberships) {
            String code = m.getSupportedLanguage().getLocaleId().getId();
            String label = m.getSupportedLanguage().getDisplayName();
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle().set("padding", "0.4rem 0");
            row.getStyle().set("border-bottom", "1px solid var(--vaadin-border-color)");
            Anchor link = new Anchor("/language/" + code, label + " (" + code + ")");
            link.getStyle().set("flex", "1 1 auto");
            link.getStyle().set("color", "var(--aura-blue-text, var(--lumo-primary-text-color))");
            link.getStyle().set("font-weight", "600");
            Span roles = new Span(roleSummary(m));
            roles.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            roles.getStyle().set("font-size", "0.85rem");
            row.add(link, roles);
            list.add(row);
        }
        card.add(list);
        return card;
    }

    private String roleSummary(HLocaleMember m) {
        StringBuilder sb = new StringBuilder();
        if (m.isCoordinator()) sb.append(getTranslation("dashboardSettings.languages.role.coordinator"));
        if (m.isReviewer()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getTranslation("dashboardSettings.languages.role.reviewer"));
        }
        if (m.isTranslator()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getTranslation("dashboardSettings.languages.role.translator"));
        }
        return sb.length() == 0
                ? getTranslation("dashboardSettings.languages.role.member") : sb.toString();
    }

    /* ---------------- Client: API key + zanata.ini ---------------- */

    private Div buildClientTab() {
        Div card = card();
        HAccount account = currentAccount().orElse(null);
        if (account == null) {
            card.add(notSignedIn());
            return card;
        }

        Paragraph intro = new Paragraph(getTranslation(
                "dashboardSettings.client.intro",
                getTranslation("brand.name")));
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        // --- API key row ---
        TextField apiKey = new TextField(getTranslation("dashboardSettings.client.apiKey"));
        apiKey.setWidth("420px");
        apiKey.setReadOnly(true);
        String currentKey = account.getApiKey();
        apiKey.setValue(currentKey == null || currentKey.isBlank()
                ? getTranslation("dashboardSettings.client.notGenerated") : currentKey);
        apiKey.getStyle().set("font-family", "monospace");

        Button copyKey = new Button(getTranslation("dashboardSettings.client.copy"),
                LineAwesomeIcon.COPY_SOLID.create(),
                e -> copyToClipboard(account.getApiKey() == null ? "" : account.getApiKey()));
        copyKey.addThemeVariants(ButtonVariant.TERTIARY);

        // --- zanata.ini area ---
        TextArea ini = new TextArea(getTranslation("dashboardSettings.client.zanataIni"));
        ini.setWidthFull();
        ini.setHeight("180px");
        ini.setReadOnly(true);
        ini.getStyle().set("font-family", "monospace");
        ini.setValue(userSettingsService.renderZanataIni(
                account.getUsername(), currentRequestUrl()));

        Button copyIni = new Button(getTranslation("dashboardSettings.client.copyZanataIni"),
                LineAwesomeIcon.COPY_SOLID.create(),
                e -> copyToClipboard(ini.getValue()));
        copyIni.addThemeVariants(ButtonVariant.TERTIARY);

        // --- generate / regenerate button (confirms before clobbering) ---
        Button generate = new Button(
                getTranslation(currentKey == null || currentKey.isBlank()
                        ? "dashboardSettings.client.generate"
                        : "dashboardSettings.client.regenerate"),
                LineAwesomeIcon.SYNC_ALT_SOLID.create(),
                e -> {
                    Runnable doIt = () -> {
                        String fresh = userSettingsService.regenerateApiKey(account.getUsername());
                        apiKey.setReadOnly(false);
                        apiKey.setValue(fresh);
                        apiKey.setReadOnly(true);
                        ini.setReadOnly(false);
                        ini.setValue(userSettingsService.renderZanataIni(
                account.getUsername(), currentRequestUrl()));
                        ini.setReadOnly(true);
                        toast(getTranslation("dashboardSettings.client.generated"), false);
                    };
                    if (currentKey == null || currentKey.isBlank()) {
                        doIt.run();
                    } else {
                        ConfirmDialog dlg = new ConfirmDialog(
                                getTranslation("dashboardSettings.client.regenConfirmTitle"),
                                getTranslation("dashboardSettings.client.regenConfirmBody",
                                        getTranslation("brand.name")),
                                getTranslation("dashboardSettings.client.regenConfirmBtn"),
                                ev -> doIt.run(),
                                getTranslation("common.cancel"), ev -> {});
                        dlg.setConfirmButtonTheme("primary error");
                        dlg.open();
                    }
                });
        generate.addThemeVariants(ButtonVariant.PRIMARY);

        card.add(intro,
                sectionTitle(getTranslation("dashboardSettings.client.apiKeySection")),
                new HorizontalLayout(apiKey, copyKey, generate) {{
                    setAlignItems(FlexComponent.Alignment.END);
                    setSpacing(true);
                }},
                spacer(),
                sectionTitle(getTranslation("dashboardSettings.client.zanataIniSection")),
                ini,
                copyIni);
        return card;
    }

    /* ---------------- helpers ---------------- */

    private static Div card() {
        Div d = new Div();
        d.setWidthFull();
        d.getStyle().set("padding", "1.25rem 1.5rem");
        d.getStyle().set("background", "var(--vaadin-background-color)");
        d.getStyle().set("border-radius", "10px");
        d.getStyle().set("box-sizing", "border-box");
        return d;
    }

    private static Span sectionTitle(String text) {
        Span s = new Span(text);
        s.getStyle().set("display", "block");
        s.getStyle().set("font-weight", "700");
        s.getStyle().set("font-size", "1.05rem");
        s.getStyle().set("margin", "0.75rem 0 0.5rem 0");
        return s;
    }

    private static Div spacer() {
        Div d = new Div();
        d.getStyle().set("height", "1.5rem");
        return d;
    }

    private Paragraph notSignedIn() {
        Paragraph p = new Paragraph(getTranslation("dashboardSettings.notSignedIn"));
        p.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        return p;
    }

    private static void toast(String msg, boolean isError) {
        Notification n = Notification.show(msg, 3000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(isError ? NotificationVariant.ERROR
                : NotificationVariant.SUCCESS);
    }

    private void copyToClipboard(String value) {
        if (value == null || value.isEmpty()) {
            toast(getTranslation("dashboardSettings.nothingToCopy"), true);
            return;
        }
        UI.getCurrent().getPage().executeJs(
                "navigator.clipboard.writeText($0)", value);
        toast(getTranslation("dashboardSettings.copiedToClipboard"), false);
    }

    /** Returns the public scheme + host[:port] of the current Vaadin request,
     *  e.g. {@code http://localhost:8080/}. Used as the zanata.ini fallback. */
    private static String currentRequestUrl() {
        com.vaadin.flow.server.VaadinRequest req =
                com.vaadin.flow.server.VaadinRequest.getCurrent();
        if (req == null) return null;
        String scheme = req.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = req.isSecure() ? "https" : "http";
        }
        String host = req.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = req.getHeader("Host");
        }
        if (host == null || host.isBlank()) return null;
        return scheme + "://" + host + "/";
    }

    private Optional<HAccount> currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken
                || auth.getName() == null) {
            return Optional.empty();
        }
        return accountRepository.findByUsername(auth.getName());
    }
}
