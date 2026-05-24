package org.zanata.spring.vaadin.profile;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.zanata.common.ContentState;
import org.zanata.model.Activity;
import org.zanata.model.HAccount;
import org.zanata.spring.repository.AccountRepository;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profile | Zanata")
@PermitAll
public class ProfileView extends VerticalLayout implements BeforeEnterObserver {

    private final AccountRepository accountRepository;
    private final ActivityRepository activityRepository;

    public ProfileView(AccountRepository accountRepository,
                       ActivityRepository activityRepository) {
        this.accountRepository = accountRepository;
        this.activityRepository = activityRepository;
        setWidthFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("max-width", "1100px");
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
        HAccount account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Account not found: " + username));

        add(buildHeader(account));
        add(buildWeeklyActivity(username));
    }

    private HorizontalLayout buildHeader(HAccount account) {
        Div avatar = new Div();
        avatar.getStyle().set("width", "96px");
        avatar.getStyle().set("height", "96px");
        avatar.getStyle().set("border-radius", "50%");
        avatar.getStyle().set("background", "var(--vaadin-color-primary)");
        avatar.getStyle().set("color", "white");
        avatar.getStyle().set("display", "flex");
        avatar.getStyle().set("align-items", "center");
        avatar.getStyle().set("justify-content", "center");
        avatar.getStyle().set("font-size", "2.5rem");
        avatar.getStyle().set("font-weight", "600");
        String initial = account.getUsername() == null || account.getUsername().isEmpty()
                ? "?" : account.getUsername().substring(0, 1).toUpperCase();
        avatar.setText(initial);

        H1 name = new H1(account.getPerson() != null && account.getPerson().getName() != null
                ? account.getPerson().getName() : account.getUsername());
        Span usernameSpan = new Span("@" + account.getUsername());
        usernameSpan.getStyle().set("color", "var(--vaadin-text-color-secondary)");
        Span emailSpan = new Span(account.getPerson() != null && account.getPerson().getEmail() != null
                ? account.getPerson().getEmail() : "");
        emailSpan.getStyle().set("color", "var(--vaadin-text-color-secondary)");

        VerticalLayout info = new VerticalLayout(name, usernameSpan, emailSpan);
        info.setPadding(false);
        info.setSpacing(false);

        HorizontalLayout layout = new HorizontalLayout(avatar, info);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        return layout;
    }

    private Div buildWeeklyActivity(String username) {
        Div panel = new Div();
        panel.setWidthFull();
        panel.getStyle().set("border", "1px solid var(--vaadin-border-color)");
        panel.getStyle().set("border-radius", "8px");
        panel.getStyle().set("padding", "1rem");
        panel.getStyle().set("margin-top", "1.5rem");

        panel.add(new H3("This week's activity"));

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
            long[] bucket = byDay.computeIfAbsent(day, k -> new long[]{0, 0, 0});
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
            long[] bucket = byDay.getOrDefault(d, new long[]{0, 0, 0});
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
            panel.add(new Paragraph("No recorded activity."));
        } else {
            Paragraph summary = new Paragraph(totalWords + " words translated in last 200 events.");
            summary.getStyle().set("margin-top", "0.75rem");
            panel.add(summary);
        }
        return panel;
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
}
