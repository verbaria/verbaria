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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
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
@PageTitle("Settings | Zanata")
@PermitAll
public class DashboardSettingsView extends VerticalLayout {

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

        H2 heading = new H2("Settings");

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add("Account", buildAccountTab());
        tabs.add("Profile", buildProfileTab());
        tabs.add("Languages", buildLanguagesTab());
        tabs.add("Client", buildClientTab());

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

        TextField email = new TextField("Email");
        if (account.getPerson() != null && account.getPerson().getEmail() != null) {
            email.setValue(account.getPerson().getEmail());
        }
        email.setWidth("360px");

        Button updateEmail = new Button("Update email", LineAwesomeIcon.SAVE_SOLID.create(),
                e -> {
                    userSettingsService.updateProfile(account.getUsername(), null, email.getValue());
                    toast("Email saved", false);
                });
        updateEmail.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        PasswordField oldPw = new PasswordField("Current password");
        PasswordField newPw = new PasswordField("New password");
        PasswordField confirmPw = new PasswordField("Confirm new password");
        Button changePw = new Button("Change password", LineAwesomeIcon.KEY_SOLID.create(), e -> {
            if (!newPw.getValue().equals(confirmPw.getValue())) {
                toast("New password and confirmation do not match", true);
                return;
            }
            if (newPw.getValue().length() < 6) {
                toast("Password must be at least 6 characters", true);
                return;
            }
            boolean ok = userSettingsService.changePassword(account.getUsername(),
                    oldPw.getValue(), newPw.getValue());
            if (ok) {
                oldPw.clear();
                newPw.clear();
                confirmPw.clear();
                toast("Password changed", false);
            } else {
                toast("Current password is incorrect", true);
            }
        });

        FormLayout pwForm = new FormLayout(oldPw, newPw, confirmPw);
        pwForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 3));

        card.add(sectionTitle("Email"));
        card.add(new HorizontalLayout(email, updateEmail) {{
            setAlignItems(FlexComponent.Alignment.END);
            setSpacing(true);
        }});
        card.add(spacer());
        card.add(sectionTitle("Change password"));
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
        TextField username = new TextField("Username");
        username.setReadOnly(true);
        username.setValue(account.getUsername() == null ? "" : account.getUsername());

        TextField displayName = new TextField("Display name");
        if (account.getPerson() != null && account.getPerson().getName() != null) {
            displayName.setValue(account.getPerson().getName());
        }

        Button save = new Button("Save profile", LineAwesomeIcon.SAVE_SOLID.create(), e -> {
            userSettingsService.updateProfile(account.getUsername(), displayName.getValue(), null);
            toast("Profile saved", false);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        FormLayout form = new FormLayout(username, displayName);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("600px", 2));
        card.add(sectionTitle("Public profile"), form, save);

        // --- per-user "allow AI translation" toggle ---
        // Hidden entirely when the admin hasn't enabled the AI feature.
        if (aiPolicy.isGloballyEnabled()) {
            card.add(spacer());
            card.add(sectionTitle("AI translation"));
            Checkbox allowAi = new Checkbox("Show \"Translate with AI\" buttons in the editor");
            allowAi.setValue(aiPolicy.isAllowedForUser(account.getUsername()));
            allowAi.addValueChangeListener(e -> {
                aiPolicy.setAllowedForUser(account.getUsername(),
                        Boolean.TRUE.equals(e.getValue()));
                toast("AI preference saved", false);
            });
            Paragraph aiHint = new Paragraph(
                    "Turn off to hide the per-row magic-wand and \"AI translate missing\" "
                            + "actions when you open a translation editor.");
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
            card.add(new Paragraph("No person profile attached to this account."));
            return card;
        }
        List<HLocaleMember> memberships =
                localeMemberRepository.findByPerson(account.getPerson());
        card.add(sectionTitle("Your language teams"));
        if (memberships.isEmpty()) {
            Paragraph p = new Paragraph(
                    "You're not in any language teams yet.");
            p.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            Anchor browse = new Anchor("/language/list", "Browse languages →");
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
            Anchor link = new Anchor("/language/view/" + code, label + " (" + code + ")");
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

    private static String roleSummary(HLocaleMember m) {
        StringBuilder sb = new StringBuilder();
        if (m.isCoordinator()) sb.append("coordinator");
        if (m.isReviewer()) { if (sb.length() > 0) sb.append(", "); sb.append("reviewer"); }
        if (m.isTranslator()) { if (sb.length() > 0) sb.append(", "); sb.append("translator"); }
        return sb.length() == 0 ? "member" : sb.toString();
    }

    /* ---------------- Client: API key + zanata.ini ---------------- */

    private Div buildClientTab() {
        Div card = card();
        HAccount account = currentAccount().orElse(null);
        if (account == null) {
            card.add(notSignedIn());
            return card;
        }

        Paragraph intro = new Paragraph(
                "An API key and zanata.ini are required to push/pull translations "
                        + "from the CLI client or the Maven plugin.");
        intro.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        // --- API key row ---
        TextField apiKey = new TextField("API key");
        apiKey.setWidth("420px");
        apiKey.setReadOnly(true);
        String currentKey = account.getApiKey();
        apiKey.setValue(currentKey == null || currentKey.isBlank()
                ? "(not generated)" : currentKey);
        apiKey.getStyle().set("font-family", "monospace");

        Button copyKey = new Button("Copy", LineAwesomeIcon.COPY_SOLID.create(),
                e -> copyToClipboard(account.getApiKey() == null ? "" : account.getApiKey()));
        copyKey.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // --- zanata.ini area ---
        TextArea ini = new TextArea("zanata.ini");
        ini.setWidthFull();
        ini.setHeight("180px");
        ini.setReadOnly(true);
        ini.getStyle().set("font-family", "monospace");
        ini.setValue(userSettingsService.renderZanataIni(
                account.getUsername(), currentRequestUrl()));

        Button copyIni = new Button("Copy zanata.ini", LineAwesomeIcon.COPY_SOLID.create(),
                e -> copyToClipboard(ini.getValue()));
        copyIni.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // --- generate / regenerate button (confirms before clobbering) ---
        Button generate = new Button(
                currentKey == null || currentKey.isBlank()
                        ? "Generate new API key" : "Regenerate API key",
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
                        toast("New API key generated", false);
                    };
                    if (currentKey == null || currentKey.isBlank()) {
                        doIt.run();
                    } else {
                        ConfirmDialog dlg = new ConfirmDialog(
                                "Regenerate API key?",
                                "The current key will stop working immediately. "
                                        + "Any CLI / Maven plugin using it will need the new key.",
                                "Regenerate", ev -> doIt.run(),
                                "Cancel", ev -> {});
                        dlg.setConfirmButtonTheme("primary error");
                        dlg.open();
                    }
                });
        generate.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        card.add(intro,
                sectionTitle("API key"),
                new HorizontalLayout(apiKey, copyKey, generate) {{
                    setAlignItems(FlexComponent.Alignment.END);
                    setSpacing(true);
                }},
                spacer(),
                sectionTitle("Configuration (zanata.ini)"),
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

    private static Paragraph notSignedIn() {
        Paragraph p = new Paragraph("Sign in to view this section.");
        p.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        return p;
    }

    private static void toast(String msg, boolean isError) {
        Notification n = Notification.show(msg, 3000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(isError ? NotificationVariant.LUMO_ERROR
                : NotificationVariant.LUMO_SUCCESS);
    }

    private static void copyToClipboard(String value) {
        if (value == null || value.isEmpty()) {
            toast("Nothing to copy", true);
            return;
        }
        UI.getCurrent().getPage().executeJs(
                "navigator.clipboard.writeText($0)", value);
        toast("Copied to clipboard", false);
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
