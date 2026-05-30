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
import org.zanata.spring.vaadin.theme.AuraUtility;

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
import org.zanata.model.HApplicationConfiguration;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.repository.ApplicationConfigurationRepository;
import org.zanata.spring.repository.LocaleMemberRepository;
import org.zanata.spring.settings.ServerSetting;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.dashboard.DashboardSettingsView;

@Route(value = "profile", layout = MainLayout.class)
@PermitAll
public class ProfileView extends VerticalLayout implements BeforeEnterObserver, TitleKey{

    @Override public String pageTitleKey() { return "page.profile"; }


    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;
    private final LocaleMemberRepository localeMemberRepository;
    private final ApplicationConfigurationRepository configRepository;

    public ProfileView(AccountRepository accountRepository,
                       ActivityRepository activityRepository,
                       LocaleMemberRepository localeMemberRepository,
                       ApplicationConfigurationRepository configRepository) {
        this.accountRepository = accountRepository;
        this.activityRepository = activityRepository;
        this.localeMemberRepository = localeMemberRepository;
        this.configRepository = configRepository;
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("max-width", "960px");
        addClassNames(AuraUtility.Margin.Horizontal.AUTO);
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
        side.addClassNames(AuraUtility.Flex.ONE);
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
        name.addClassNames(AuraUtility.Margin.NONE);
        Span usernameSpan = new Span("@" + account.getUsername());
        usernameSpan.addClassNames(AuraUtility.TextColor.SECONDARY);
        Span emailSpan = new Span(email == null ? "" : email);
        emailSpan.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);

        // Role chips (admin/user).
        HorizontalLayout chips = new HorizontalLayout();
        chips.setSpacing(true);
        chips.setPadding(false);
        chips.addClassNames(AuraUtility.Margin.Top.SMALL);
        if (account.getRoles() != null) {
            account.getRoles().forEach(r -> chips.add(roleChip(r.getName())));
        }

        VerticalLayout info = new VerticalLayout(name, usernameSpan, emailSpan, chips);
        info.setPadding(false);
        info.setSpacing(false);
        info.addClassNames(AuraUtility.Flex.AUTO);

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
        // Vaadin Avatar handles the circular shape, initials fallback, and
        // image loading — when no image URL is set it falls back to the
        // first-letter abbreviation of `name`. We feed it Gravatar when an
        // email is on file, otherwise leave imageless for the initials.
        com.vaadin.flow.component.avatar.Avatar avatar =
                new com.vaadin.flow.component.avatar.Avatar(displayName);
        avatar.setHeight("96px");
        avatar.setWidth("96px");
        avatar.addClassNames(AuraUtility.Flex.NONE);
        if (email != null) {
            String hash = md5LowerHex(email.trim().toLowerCase(Locale.ROOT));
            avatar.setImage("https://www.gravatar.com/avatar/" + hash
                    + "?s=160&d=identicon&r=" + gravatarRating());
        }
        return avatar;
    }

    /**
     * Configured Gravatar max rating ({@code gravatar.rating}); defaults to
     * {@code g}. Restricted to Gravatar's allowed values so a bad config can't
     * produce a broken URL.
     */
    private String gravatarRating() {
        return configRepository
                .findByKey(ServerSetting.GRAVATAR_RATING.key())
                .map(HApplicationConfiguration::getValue)
                .map(v -> v == null ? "" : v.trim().toLowerCase(Locale.ROOT))
                .filter(v -> v.equals("g") || v.equals("pg")
                        || v.equals("r") || v.equals("x"))
                .orElse("g");
    }

    private Span roleChip(String role) {
        String text = role == null ? "" : role.replace("ROLE_", "").toLowerCase();
        Span chip = new Span(text);
        chip.addClassNames(AuraUtility.Background.CONTRAST_5, AuraUtility.TextColor.BODY, AuraUtility.BorderRadius.FULL);
        chip.getStyle().set("padding", "0.15rem 0.6rem");
        chip.addClassNames(AuraUtility.FontSize.XSMALL, AuraUtility.FontWeight.SEMIBOLD);
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
            empty.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Top.SMALL);
            panel.add(empty);
        } else {
            Paragraph summary = new Paragraph(getTranslation("profile.wordsTranslatedSummary", totalWords));
            summary.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.Margin.Top.SMALL);
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
            p.addClassNames(AuraUtility.TextColor.SECONDARY);
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
            link.addClassNames(AuraUtility.Flex.AUTO, AuraUtility.TextColor.PRIMARY, AuraUtility.FontWeight.SEMIBOLD);
            Span role = new Span(roleSummary(m));
            role.addClassNames(AuraUtility.TextColor.SECONDARY, AuraUtility.FontSize.SMALL);
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
        a.addClassNames(AuraUtility.Display.BLOCK);
        return a;
    }

    /* ---------------- Helpers ---------------- */

    private static void cardStyle(Div card) {
        card.setWidthFull();
        card.addClassNames(AuraUtility.Background.BASE, AuraUtility.Border.ALL, AuraUtility.BorderColor.DEFAULT, AuraUtility.BorderRadius.MEDIUM, AuraUtility.Padding.LARGE);
        card.getStyle().set("box-shadow", "0 1px 2px rgba(0,0,0,0.04)");
        card.addClassNames(AuraUtility.BoxSizing.BORDER);
    }

    private Div cell(String text, String color, boolean bold) {
        Div d = new Div();
        d.setText(text);
        d.addClassNames(AuraUtility.Flex.ONE, AuraUtility.TextAlignment.CENTER, AuraUtility.Padding.SMALL);
        d.getStyle().set("color", color);
        if (bold) d.addClassNames(AuraUtility.FontWeight.SEMIBOLD);
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
