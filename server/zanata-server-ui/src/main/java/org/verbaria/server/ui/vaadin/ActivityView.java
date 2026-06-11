package org.verbaria.server.ui.vaadin;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridLazyDataView;
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

    @Override public String pageTitleKey() { return "page.activityFeed"; }

    private final ActivityFeedService activityFeed;
    private final AvatarService avatarService;
    private final ComboBox<Actor> userFilter = new ComboBox<>();
    private final ComboBox<ProjectOption> projectFilter = new ComboBox<>();
    private final ComboBox<LocaleOption> localeFilter = new ComboBox<>();
    private final DatePicker fromFilter = new DatePicker();
    private final DatePicker toFilter = new DatePicker();
    private final Grid<Entry> grid = new Grid<>(Entry.class, false);
    private final SimpleDateFormat when = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private GridLazyDataView<Entry> dataView;

    public ActivityView(ActivityFeedService activityFeed,
                        AvatarService avatarService) {
        this.activityFeed = activityFeed;
        this.avatarService = avatarService;
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        userFilter.setPlaceholder(getTranslation("activity.filter.user"));
        userFilter.setClearButtonVisible(true);
        userFilter.setItems(activityFeed.actors());
        userFilter.setItemLabelGenerator(Actor::displayName);
        userFilter.addValueChangeListener(e -> dataView.refreshAll());

        projectFilter.setPlaceholder(getTranslation("activity.filter.project"));
        projectFilter.setClearButtonVisible(true);
        projectFilter.setItems(activityFeed.projects());
        projectFilter.setItemLabelGenerator(ProjectOption::name);
        projectFilter.addValueChangeListener(e -> dataView.refreshAll());

        localeFilter.setPlaceholder(getTranslation("activity.filter.locale"));
        localeFilter.setClearButtonVisible(true);
        localeFilter.setItems(activityFeed.locales());
        localeFilter.setItemLabelGenerator(LocaleOption::displayName);
        localeFilter.addValueChangeListener(e -> dataView.refreshAll());

        fromFilter.setPlaceholder(getTranslation("activity.filter.from"));
        fromFilter.setLabel(getTranslation("activity.filter.from"));
        fromFilter.setClearButtonVisible(true);
        fromFilter.addValueChangeListener(e -> dataView.refreshAll());

        toFilter.setPlaceholder(getTranslation("activity.filter.to"));
        toFilter.setLabel(getTranslation("activity.filter.to"));
        toFilter.setClearButtonVisible(true);
        toFilter.addValueChangeListener(e -> dataView.refreshAll());

        HorizontalLayout filters = new HorizontalLayout(
                userFilter, projectFilter, localeFilter, fromFilter, toFilter);
        filters.setAlignItems(FlexComponent.Alignment.END);
        filters.setSpacing(true);

        grid.addColumn(e -> e.when() == null ? "" : when.format(e.when()))
                .setHeader(getTranslation("activity.col.when"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::userCell)
                .setHeader(getTranslation("activity.col.user"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actionCell)
                .setHeader(getTranslation("activity.col.action"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::keyCell)
                .setHeader(getTranslation("activity.col.key"))
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

        // Lazy, backend-paged, UNDEFINED-SIZE provider: the grid fetches one
        // page at a time and detects the end when a page returns fewer rows
        // than asked. No count query — counting the whole feed would be a full
        // table scan that no index can avoid. A filter change re-queries via
        // dataView.refreshAll().
        dataView = grid.setItems(query -> activityFeed.recent(
                filterUser(), filterProject(), filterLocale(),
                filterFrom(), filterTo(),
                query.getOffset(), query.getLimit()).stream());

        add(filters);
        addAndExpand(grid);
    }

    private String filterUser() {
        Actor u = userFilter.getValue();
        return u == null ? null : u.username();
    }

    private String filterProject() {
        ProjectOption p = projectFilter.getValue();
        return p == null ? null : p.slug();
    }

    private String filterLocale() {
        LocaleOption l = localeFilter.getValue();
        return l == null ? null : l.id();
    }

    private Date filterFrom() {
        return toDate(fromFilter.getValue());
    }

    private Date filterTo() {
        // Inclusive end: add a day so the chosen date is fully covered.
        return toDate(toFilter.getValue() == null ? null
                : toFilter.getValue().plusDays(1));
    }

    private static Date toDate(LocalDate date) {
        return date == null ? null
                : Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Component userCell(Entry e) {
        String name = e.actorName() == null ? e.actorUsername() : e.actorName();
        Avatar av = avatarService.avatar(name, e.actorEmail(), 24);
        Span label = new Span(name == null ? "" : name);
        HorizontalLayout row = new HorizontalLayout(av, label);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setSpacing(false);
        row.addClassNames(AuraUtility.Gap.SMALL);
        return row;
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
        String value = e.value();
        boolean empty = value == null || value.isBlank();
        String prev = e.previousValue();
        // Only show "before → after" when there's a distinct previous value;
        // an unchanged value (AA → AA) adds nothing.
        if (prev != null && !prev.isBlank() && !prev.equals(value)) {
            Span before = new Span(prev);
            before.addClassNames(AuraUtility.TextColor.SECONDARY,
                    AuraUtility.TextDecoration.LINE_THROUGH);
            Span arrow = new Span("→");
            arrow.addClassNames(AuraUtility.TextColor.TERTIARY);
            d.add(before, arrow);
        }
        if (empty) {
            // Saved with no translation — fall back to the source text so the
            // row isn't blank.
            Span src = new Span(e.source() == null || e.source().isBlank()
                    ? getTranslation("activity.value.empty") : e.source());
            src.addClassNames(AuraUtility.TextColor.SECONDARY,
                    AuraUtility.FontStyle.ITALIC);
            d.add(src);
        } else {
            Span after = new Span(value);
            after.addClassNames(AuraUtility.TextColor.BODY,
                    AuraUtility.FontWeight.MEDIUM);
            d.add(after);
        }
        return d;
    }

    private Anchor keyCell(Entry e) {
        String label = e.key() == null || e.key().isBlank()
                ? e.resId() : e.key();
        Anchor a = new Anchor(translateLink(e), label);
        a.addClassNames(AuraUtility.TextColor.PRIMARY,
                AuraUtility.TextDecoration.NONE, AuraUtility.FontWeight.MEDIUM);
        return a;
    }

    private static String translateLink(Entry e) {
        String url = "/translate/" + e.projectSlug() + "/" + e.versionSlug()
                + "/" + e.localeId() + "?doc=" + e.docId();
        if (e.resId() != null && !e.resId().isBlank()) {
            url += "&q=" + e.resId();
        }
        return url;
    }

    private Anchor whereCell(Entry e) {
        String label = (e.projectName() == null ? e.projectSlug() : e.projectName())
                + " › " + e.versionSlug() + " › " + e.docId();
        Anchor a = new Anchor(translateLink(e), label);
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
