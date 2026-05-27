package org.zanata.spring.vaadin.profile;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.PermitAll;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.model.Activity;
import org.zanata.model.HAccount;
import org.zanata.model.HLocaleMember;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.dashboard.DashboardSettingsView;

@Route(value = "profile", layout = MainLayout.class)
@PermitAll
public class ProfileView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.profile"; }


    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;
    private final LocaleMemberRepository localeMemberRepository;

    public ProfileView(AccountRepository accountRepository,
                       ActivityRepository activityRepository,
                       LocaleMemberRepository localeMemberRepository) {
        this.accountRepository = accountRepository;
        this.activityRepository = activityRepository;
        this.localeMemberRepository = localeMemberRepository;
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("max-width", "960px");
        getStyle().set("margin", "0 auto");
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof AnonymousAuthenticationToken) {
            throw new NotFoundException("Not signed in");
        }
        String username = auth.getName();
        // Use the *WithRoles* fetch so the role chips can be rendered outside
        // any open Hibernate session (the role collection is LAZY).
        HAccount account = accountRepository.findByUsernameWithRoles(username)
                .orElseThrow(() -> new NotFoundException("Account not found: " + username));

        add(buildHeaderCard(account));

        // Two-column body: activity (left) + sidebar with languages and quick links.
        HorizontalLayout body = new HorizontalLayout();
        body.setWidthFull();
        body.setSpacing(true);
        body.setAlignItems(FlexComponent.Alignment.START);

        Div activity = buildWeeklyActivity(username);
        activity.getStyle().set("flex", "2 1 0");

        VerticalLayout side = new VerticalLayout(
                buildLanguagesPanel(account),
                buildQuickLinks());
        side.setPadding(false);
        side.setSpacing(true);
        side.getStyle().set("flex", "1 1 0");
        side.getStyle().set("min-width", "240px");

        body.add(activity, side);
        add(body);
    }

    /* ---------------- Header ---------------- */

    private Div buildHeaderCard(HAccount account) {
        String displayName = account.getPerson() != null
                && account.getPerson().getName() != null
                ? account.getPerson().getName() : account.getUsername();
        String email = account.getPerson() != null ? account.getPerson().getEmail() : null;

        Component avatar = buildAvatar(displayName, email);

        H1 name = new H1(displayName);
        name.getStyle().set("margin", "0");
        Span usernameSpan = new Span("@" + account.getUsername());
        usernameSpan.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        Span emailSpan = new Span(email == null ? "" : email);
        emailSpan.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        emailSpan.getStyle().set("font-size", "0.9rem");

        // Role chips (admin/user).
        HorizontalLayout chips = new HorizontalLayout();
        chips.setSpacing(true);
        chips.setPadding(false);
        chips.getStyle().set("margin-top", "0.5rem");
        if (account.getRoles() != null) {
            account.getRoles().forEach(r -> chips.add(roleChip(r.getName())));
        }

        VerticalLayout info = new VerticalLayout(name, usernameSpan, emailSpan, chips);
        info.setPadding(false);
        info.setSpacing(false);
        info.getStyle().set("flex", "1 1 auto");

        Button settings = new Button(getTranslation("profile.settings"),
                LineAwesomeIcon.COG_SOLID.create(),
                e -> UI.getCurrent().navigate(DashboardSettingsView.class));
        settings.addThemeVariants(ButtonVariant.TERTIARY);
        VerticalLayout actions = new VerticalLayout(settings);
        actions.setPadding(false);
        actions.setSpacing(false);
        actions.setAlignItems(FlexComponent.Alignment.END);

        HorizontalLayout row = new HorizontalLayout(avatar, info, actions);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setSpacing(true);
        row.setWidthFull();

        Div card = new Div(row);
        cardStyle(card);
        return card;
    }

    private Component buildAvatar(String displayName, String email) {
        // Prefer Gravatar (matches legacy behavior); fall back to an
        // initials disk if no email is on file.
        String hash = email == null ? null : md5LowerHex(email.trim().toLowerCase(Locale.ROOT));
        if (hash != null) {
            String url = "https://www.gravatar.com/avatar/" + hash + "?s=160&d=identicon&r=g";
            Image img = new Image(url, displayName);
            img.setWidth("96px");
            img.setHeight("96px");
            img.getStyle().set("border-radius", "50%");
            img.getStyle().set("flex", "0 0 auto");
            return img;
        }
        Div disk = new Div();
        disk.getStyle().set("width", "96px");
        disk.getStyle().set("height", "96px");
        disk.getStyle().set("flex", "0 0 auto");
        disk.getStyle().set("border-radius", "50%");
        disk.getStyle().set("background", "var(--aura-blue-text, var(--lumo-primary-color))");
        disk.getStyle().set("color", "white");
        disk.getStyle().set("display", "flex");
        disk.getStyle().set("align-items", "center");
        disk.getStyle().set("justify-content", "center");
        disk.getStyle().set("font-size", "2.5rem");
        disk.getStyle().set("font-weight", "700");
        disk.setText(displayName == null || displayName.isEmpty()
                ? "?" : displayName.substring(0, 1).toUpperCase());
        return disk;
    }

    private Span roleChip(String role) {
        String text = role == null ? "" : role.replace("ROLE_", "").toLowerCase();
        Span chip = new Span(text);
        chip.getStyle().set("background", "var(--vaadin-background-container)");
        chip.getStyle().set("color", "var(--vaadin-text-color)");
        chip.getStyle().set("border-radius", "999px");
        chip.getStyle().set("padding", "0.15rem 0.6rem");
        chip.getStyle().set("font-size", "0.75rem");
        chip.getStyle().set("font-weight", "600");
        return chip;
    }

    /* ---------------- Activity ---------------- */

    private Div buildWeeklyActivity(String username) {
        Div panel = new Div();
        cardStyle(panel);
        panel.add(new H3(getTranslation("profile.weekActivity")));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate weekStart = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1);

        List<Activity> recent = activityRepository.findByActor(
                username, PageRequest.of(0, 200));
        Map<LocalDate, long[]> byDay = new HashMap<>();
        for (Activity a : recent) {
            if (a.getApproxTime() == null) continue;
            LocalDate day = a.getApproxTime().toInstant().atZone(zone).toLocalDate();
            if (day.isBefore(weekStart) || day.isAfter(today)) continue;
            long[] bucket = byDay.computeIfAbsent(day, k -> new long[]{0});
            if (a.getWordCount() > 0) bucket[0] += a.getWordCount();
        }

        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("d");
        DateTimeFormatter dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);

        HorizontalLayout headerRow = new HorizontalLayout();
        headerRow.setWidthFull();
        HorizontalLayout dayRow = new HorizontalLayout();
        dayRow.setWidthFull();
        HorizontalLayout countRow = new HorizontalLayout();
        countRow.setWidthFull();

        for (int i = 0; i < 7; i++) {
            LocalDate d = weekStart.plusDays(i);
            long[] bucket = byDay.getOrDefault(d, new long[]{0});
            long words = bucket[0];

            Div dowCell = cell(dowFmt.format(d), "var(--vaadin-text-color-secondary)", false);
            Div dayCell = cell(dayFmt.format(d), "var(--vaadin-text-color)", true);
            Div countCell = cell(String.valueOf(words),
                    words > 0 ? "var(--aura-green)" : "var(--vaadin-text-color-secondary)", true);

            headerRow.add(dowCell);
            dayRow.add(dayCell);
            countRow.add(countCell);
        }

        panel.add(headerRow, dayRow, countRow);

        long totalWords = recent.stream().mapToLong(Activity::getWordCount).sum();
        if (recent.isEmpty()) {
            Paragraph empty = new Paragraph(getTranslation("profile.noActivityRecorded"));
            empty.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            empty.getStyle().set("margin-top", "0.5rem");
            panel.add(empty);
        } else {
            Paragraph summary = new Paragraph(getTranslation("profile.wordsTranslatedSummary", totalWords));
            summary.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            summary.getStyle().set("margin-top", "0.5rem");
            panel.add(summary);
        }
        return panel;
    }

    /* ---------------- Languages ---------------- */

    private Div buildLanguagesPanel(HAccount account) {
        Div panel = new Div();
        cardStyle(panel);
        panel.add(new H3(getTranslation("profile.languagesHeading")));

        List<HLocaleMember> memberships = account.getPerson() == null
                ? List.of()
                : localeMemberRepository.findByPerson(account.getPerson());
        if (memberships.isEmpty()) {
            Paragraph p = new Paragraph(getTranslation("profile.notInTeams"));
            p.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            panel.add(p);
            Anchor join = new Anchor("/languages", getTranslation("profile.browseLanguages"));
            panel.add(join);
            return panel;
        }
        VerticalLayout rows = new VerticalLayout();
        rows.setPadding(false);
        rows.setSpacing(false);
        for (HLocaleMember m : memberships) {
            String code = m.getSupportedLanguage().getLocaleId().getId();
            String label = m.getSupportedLanguage().getDisplayName();
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            Anchor link = new Anchor("/language/" + code, label);
            link.getStyle().set("flex", "1 1 auto");
            link.getStyle().set("color", "var(--aura-blue-text, var(--lumo-primary-text-color))");
            link.getStyle().set("font-weight", "600");
            Span role = new Span(roleSummary(m));
            role.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            role.getStyle().set("font-size", "0.8rem");
            row.add(link, role);
            rows.add(row);
        }
        panel.add(rows);
        return panel;
    }

    private String roleSummary(HLocaleMember m) {
        StringBuilder sb = new StringBuilder();
        if (m.isCoordinator()) sb.append(getTranslation("profile.role.coord"));
        if (m.isReviewer()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getTranslation("profile.role.review"));
        }
        if (m.isTranslator()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(getTranslation("profile.role.trans"));
        }
        return sb.length() == 0 ? getTranslation("profile.role.member") : sb.toString();
    }

    /* ---------------- Quick links ---------------- */

    private Div buildQuickLinks() {
        Div panel = new Div();
        cardStyle(panel);
        panel.add(new H3(getTranslation("profile.quickLinks")));
        VerticalLayout list = new VerticalLayout(
                link(getTranslation("profile.quickLink.dashboard"), "/dashboard"),
                link(getTranslation("profile.quickLink.accountApi"), "/dashboard/settings"),
                link(getTranslation("profile.quickLink.browseLanguages"), "/languages"));
        list.setPadding(false);
        list.setSpacing(false);
        panel.add(list);
        return panel;
    }

    private Anchor link(String label, String href) {
        Anchor a = new Anchor(href, label);
        a.getStyle().set("padding", "0.35rem 0");
        a.getStyle().set("display", "block");
        return a;
    }

    /* ---------------- Helpers ---------------- */

    private static void cardStyle(Div card) {
        card.setWidthFull();
        card.getStyle().set("background", "var(--vaadin-background-color)");
        card.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        card.getStyle().set("border-radius", "10px");
        card.getStyle().set("padding", "1.25rem 1.5rem");
        card.getStyle().set("box-shadow", "0 1px 2px rgba(0,0,0,0.04)");
        card.getStyle().set("box-sizing", "border-box");
    }

    private Div cell(String text, String color, boolean bold) {
        Div d = new Div();
        d.setText(text);
        d.getStyle().set("flex", "1");
        d.getStyle().set("text-align", "center");
        d.getStyle().set("padding", "0.4rem");
        d.getStyle().set("color", color);
        if (bold) d.getStyle().set("font-weight", "600");
        return d;
    }

    private static String md5LowerHex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
