package org.verbaria.server.ui.vaadin;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.zanata.common.ContentState;
import org.verbaria.server.headless.service.ActivityFeedService;
import org.verbaria.server.headless.service.ActivityFeedService.Actor;
import org.verbaria.server.headless.service.ActivityFeedService.Entry;
import org.verbaria.server.headless.service.ActivityFeedService.LocaleOption;
import org.verbaria.server.headless.service.ActivityFeedService.ProjectOption;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;

/**
 * Global activity feed: shows what users did — saved, approved, rejected or
 * marked needs-review — across all projects, filterable by user and project.
 */
@Route(value = "activity", layout = MainLayout.class)
@AnonymousAllowed
public class ActivityView extends VerticalLayout implements TitleKey {

    private static final int LIMIT = 200;

    @Override public String pageTitleKey() { return "page.activityFeed"; }

    private final ActivityFeedService activityFeed;
    private final ComboBox<Actor> userFilter = new ComboBox<>();
    private final ComboBox<ProjectOption> projectFilter = new ComboBox<>();
    private final ComboBox<LocaleOption> localeFilter = new ComboBox<>();
    private final DatePicker fromFilter = new DatePicker();
    private final DatePicker toFilter = new DatePicker();
    private final Grid<Entry> grid = new Grid<>(Entry.class, false);
    private final SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public ActivityView(ActivityFeedService activityFeed) {
        this.activityFeed = activityFeed;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        userFilter.setPlaceholder(getTranslation("activity.filter.user"));
        userFilter.setClearButtonVisible(true);
        userFilter.setItems(activityFeed.actors());
        userFilter.setItemLabelGenerator(Actor::displayName);
        userFilter.addValueChangeListener(e -> reload());

        projectFilter.setPlaceholder(getTranslation("activity.filter.project"));
        projectFilter.setClearButtonVisible(true);
        projectFilter.setItems(activityFeed.projects());
        projectFilter.setItemLabelGenerator(ProjectOption::name);
        projectFilter.addValueChangeListener(e -> reload());

        localeFilter.setPlaceholder(getTranslation("activity.filter.locale"));
        localeFilter.setClearButtonVisible(true);
        localeFilter.setItems(activityFeed.locales());
        localeFilter.setItemLabelGenerator(LocaleOption::displayName);
        localeFilter.addValueChangeListener(e -> reload());

        fromFilter.setPlaceholder(getTranslation("activity.filter.from"));
        fromFilter.setLabel(getTranslation("activity.filter.from"));
        fromFilter.setClearButtonVisible(true);
        fromFilter.addValueChangeListener(e -> reload());

        toFilter.setPlaceholder(getTranslation("activity.filter.to"));
        toFilter.setLabel(getTranslation("activity.filter.to"));
        toFilter.setClearButtonVisible(true);
        toFilter.addValueChangeListener(e -> reload());

        HorizontalLayout filters = new HorizontalLayout(
                userFilter, projectFilter, localeFilter, fromFilter, toFilter);
        filters.setAlignItems(FlexComponent.Alignment.END);
        filters.setSpacing(true);

        grid.addColumn(e -> e.when() == null ? "" : when.format(e.when()))
                .setHeader(getTranslation("activity.col.when"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addColumn(e -> e.actorName() == null ? e.actorUsername() : e.actorName())
                .setHeader(getTranslation("activity.col.user"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actionCell)
                .setHeader(getTranslation("activity.col.action"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::changeCell)
                .setHeader(getTranslation("activity.col.change")).setFlexGrow(2);
        grid.addComponentColumn(this::whereCell)
                .setHeader(getTranslation("activity.col.where")).setFlexGrow(1);
        grid.addColumn(Entry::localeId)
                .setHeader(getTranslation("activity.col.locale"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.setSizeFull();
        grid.addClassNames(AuraUtility.MinHeight.NONE);

        add(filters);
        addAndExpand(grid);
        reload();
    }

    private void reload() {
        Actor u = userFilter.getValue();
        ProjectOption p = projectFilter.getValue();
        LocaleOption l = localeFilter.getValue();
        List<Entry> entries = activityFeed.recent(
                u == null ? null : u.username(),
                p == null ? null : p.slug(),
                l == null ? null : l.id(),
                toDate(fromFilter.getValue()),
                toDate(toFilter.getValue() == null ? null
                        : toFilter.getValue().plusDays(1)),
                LIMIT);
        grid.setItems(entries);
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null
                : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Span actionCell(Entry e) {
        Span s = new Span(getTranslation(actionKey(e.state())));
        s.addClassNames(actionColor(e.state()), AuraUtility.FontWeight.SEMIBOLD);
        return s;
    }

    private Div changeCell(Entry e) {
        Div d = new Div();
        d.addClassNames(AuraUtility.Display.INLINE_FLEX, AuraUtility.Gap.SMALL,
                AuraUtility.AlignItems.CENTER, AuraUtility.FlexWrap.WRAP);
        String prev = e.previousValue();
        if (prev != null && !prev.isEmpty()) {
            Span before = new Span(prev);
            before.addClassNames(AuraUtility.TextColor.SECONDARY,
                    AuraUtility.TextDecoration.LINE_THROUGH);
            Span arrow = new Span("→");
            arrow.addClassNames(AuraUtility.TextColor.TERTIARY);
            d.add(before, arrow);
        }
        String value = e.value();
        Span after = new Span(value == null || value.isEmpty()
                ? getTranslation("activity.value.empty") : value);
        after.addClassNames(AuraUtility.TextColor.BODY,
                AuraUtility.FontWeight.MEDIUM);
        d.add(after);
        return d;
    }

    private Anchor whereCell(Entry e) {
        String label = (e.projectName() == null ? e.projectSlug() : e.projectName())
                + " › " + e.versionSlug() + " › " + e.docId();
        Anchor a = new Anchor("/translate/" + e.projectSlug() + "/"
                + e.versionSlug() + "/" + e.localeId() + "?doc=" + e.docId(),
                label);
        a.addClassNames(AuraUtility.TextColor.BODY,
                AuraUtility.TextDecoration.NONE);
        return a;
    }

    private static String actionKey(ContentState s) {
        return switch (s) {
            case Approved -> "activity.action.approved";
            case Rejected -> "activity.action.rejected";
            case Translated -> "activity.action.saved";
            case NeedReview -> "activity.action.needsReview";
            default -> "activity.action.changed";
        };
    }

    private static String actionColor(ContentState s) {
        return switch (s) {
            case Approved, Translated -> AuraUtility.TextColor.SUCCESS;
            case NeedReview -> AuraUtility.TextColor.ORANGE;
            case Rejected -> AuraUtility.TextColor.ERROR;
            default -> AuraUtility.TextColor.SECONDARY;
        };
    }
}
