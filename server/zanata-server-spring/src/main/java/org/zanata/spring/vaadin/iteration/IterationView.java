package org.zanata.spring.vaadin.iteration;

import java.util.List;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.NotFoundException;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.common.EntityStatus;
import org.zanata.model.HDocument;
import org.zanata.model.HProjectIteration;
import org.zanata.spring.repository.DocumentRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.ProjectIterationRepository;
import org.zanata.spring.repository.TextFlowTargetRepository;
import org.zanata.spring.vaadin.ExploreView;
import org.zanata.spring.vaadin.MainLayout;
import org.zanata.spring.vaadin.project.ProjectView;
import org.zanata.spring.vaadin.stats.IterationStats;

@Route(value = "project/:projectSlug/version/:versionSlug", layout = MainLayout.class)
@RouteAlias(value = "iteration/view/:projectSlug/:versionSlug", layout = MainLayout.class)
@PageTitle("Project version | Zanata")
@AnonymousAllowed
public class IterationView extends VerticalLayout implements BeforeEnterObserver {

    private final ProjectIterationRepository iterationRepository;
    private final DocumentRepository documentRepository;
    private final TextFlowTargetRepository targetRepository;
    private final LocaleRepository localeRepository;

    private String currentProjectSlug;
    private String currentVersionSlug;

    public IterationView(ProjectIterationRepository iterationRepository,
                         DocumentRepository documentRepository,
                         TextFlowTargetRepository targetRepository,
                         LocaleRepository localeRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
        setSizeFull();
        setPadding(true);
        setSpacing(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        removeAll();
        String projectSlug = event.getRouteParameters().get("projectSlug").orElse("");
        String versionSlug = event.getRouteParameters().get("versionSlug").orElse("");
        this.currentProjectSlug = projectSlug;
        this.currentVersionSlug = versionSlug;

        HProjectIteration iteration = iterationRepository
                .findFullByProjectAndSlug(projectSlug, versionSlug)
                .orElseThrow(() -> new NotFoundException(
                        "Version not found: " + projectSlug + "/" + versionSlug));

        IterationStats stats = IterationStats.compute(iteration.getId(),
                iterationRepository, targetRepository, localeRepository);
        List<HDocument> documents = documentRepository.findByVersion(projectSlug, versionSlug);

        add(buildBreadcrumb(projectSlug));
        add(buildHeading(iteration));
        add(buildStatsRow(stats));
        ProgressBar bar = new ProgressBar(0.0, 1.0, stats.translatedPct / 100.0);
        bar.setWidthFull();
        bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
        add(bar);
        Paragraph total = new Paragraph(
                "Total source content: " + String.format("%,d", stats.totalSourceWords) + " words");
        total.addClassNames(LumoUtility.Margin.Vertical.SMALL);
        add(total);

        TabSheet tabs = new TabSheet();
        tabs.setWidthFull();
        tabs.add(tabWithBadge("Languages", stats.localeCount),
                buildLanguagesPanel(stats));
        tabs.add(tabWithBadge("Documents", documents.size()),
                buildDocumentsPanel(documents));
        add(tabs);
    }

    private Span buildBreadcrumb(String projectSlug) {
        Span crumb = new Span();
        crumb.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        crumb.add(new RouterLink("Projects", ExploreView.class));
        crumb.add(new Span(" \u203A "));
        crumb.add(new RouterLink(projectSlug, ProjectView.class,
                new RouteParameters(new RouteParam("slug", projectSlug))));
        return crumb;
    }

    private HorizontalLayout buildHeading(HProjectIteration iteration) {
        H1 slug = new H1(iteration.getSlug());
        slug.addClassNames(LumoUtility.Margin.NONE);
        HorizontalLayout layout = new HorizontalLayout(slug);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.setSpacing(true);
        if (iteration.getStatus() == EntityStatus.READONLY) {
            var lock = LineAwesomeIcon.LOCK_SOLID.create();
            lock.setSize("0.9em");
            lock.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            layout.add(lock);
        }
        return layout;
    }

    private HorizontalLayout buildStatsRow(IterationStats stats) {
        HorizontalLayout row = new HorizontalLayout();
        row.setSpacing(true);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        row.add(statBlock(String.format("%.2f%%", stats.approvedPct), "approved",
                "var(--aura-green)"));
        row.add(statBlock(String.format("%.2f%%", stats.translatedPct), "translated",
                "var(--aura-green)"));
        row.add(statBlock(String.format("%.2f", stats.hoursRemaining),
                "total hours remaining", "var(--vaadin-text-color)"));
        return row;
    }

    private VerticalLayout statBlock(String value, String label, String color) {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false);
        col.setSpacing(false);
        col.setAlignItems(FlexComponent.Alignment.START);
        H2 valueEl = new H2(value);
        valueEl.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.XXLARGE);
        valueEl.getStyle().set("color", color);
        Span labelEl = new Span(label);
        labelEl.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        col.add(valueEl, labelEl);
        col.getStyle().set("padding-inline-end", "2rem");
        return col;
    }

    private Tab tabWithBadge(String label, int count) {
        Span badge = new Span(String.valueOf(count));
        badge.getElement().getThemeList().add("badge contrast small");
        badge.getStyle().set("margin-inline-start", "0.5rem");
        return new Tab(new Span(label), badge);
    }

    private Div buildLanguagesPanel(IterationStats stats) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");

        com.vaadin.flow.component.select.Select<String> sortSelect =
                new com.vaadin.flow.component.select.Select<>();
        sortSelect.setItems("Name", "Locale", "Translated %", "Approved %");
        sortSelect.setValue("Locale");
        sortSelect.setWidth("180px");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        H3 title = new H3("Languages");
        title.addClassNames(LumoUtility.Margin.NONE);
        HorizontalLayout sortBox = new HorizontalLayout(
                new Span("Sort"), sortSelect);
        sortBox.setAlignItems(FlexComponent.Alignment.CENTER);
        sortBox.setSpacing(true);
        header.add(title, sortBox);
        panel.add(header);

        Span counter = new Span(stats.localeCount + " languages");
        counter.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        counter.getStyle().set("display", "block");
        counter.getStyle().set("margin", "0.5rem 0 0.75rem 0");
        panel.add(counter);

        Div listContainer = new Div();
        listContainer.setWidthFull();
        renderLocaleList(listContainer, stats.perLocale, sortSelect.getValue());
        panel.add(listContainer);

        sortSelect.addValueChangeListener(e ->
                renderLocaleList(listContainer, stats.perLocale, e.getValue()));
        return panel;
    }

    private void renderLocaleList(Div container,
                                  List<IterationStats.LocaleStats> rows, String sortBy) {
        container.removeAll();
        java.util.Comparator<IterationStats.LocaleStats> cmp = switch (sortBy == null ? "" : sortBy) {
            case "Name" -> java.util.Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> {
                        String d = ls.locale.getDisplayName();
                        return d == null || d.isBlank()
                                ? (ls.locale.getLocaleId() == null ? ""
                                        : ls.locale.getLocaleId().getId())
                                : d;
                    },
                    String.CASE_INSENSITIVE_ORDER);
            case "Translated %" -> java.util.Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.translatedPct).reversed();
            case "Approved %" -> java.util.Comparator.comparingDouble(
                    (IterationStats.LocaleStats ls) -> ls.totalSourceWords == 0 ? 0.0
                            : (double) ls.approvedWords / ls.totalSourceWords * 100.0).reversed();
            default -> java.util.Comparator.comparing(
                    (IterationStats.LocaleStats ls) -> ls.locale.getLocaleId() == null
                            ? "" : ls.locale.getLocaleId().getId(),
                    String.CASE_INSENSITIVE_ORDER);
        };
        rows.stream().sorted(cmp).forEach(ls -> container.add(buildLocaleRow(ls)));
    }

    private Div buildLocaleRow(IterationStats.LocaleStats ls) {
        Div card = new Div();
        card.addClassNames(LumoUtility.Padding.SMALL, LumoUtility.Border.BOTTOM,
                LumoUtility.BorderColor.CONTRAST_10);
        card.getStyle().set("width", "100%");
        card.getStyle().set("cursor", "pointer");
        String localeIdStr = ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId();
        if (!localeIdStr.isBlank()
                && currentProjectSlug != null && currentVersionSlug != null) {
            card.addClickListener(e -> getUI().ifPresent(ui -> ui.navigate(
                    "translate/" + currentProjectSlug + "/" + currentVersionSlug
                            + "/" + localeIdStr)));
        }

        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);

        VerticalLayout left = new VerticalLayout();
        left.setPadding(false);
        left.setSpacing(false);
        String display = ls.locale.getDisplayName();
        if (display == null || display.isBlank()) {
            display = ls.locale.getLocaleId() == null ? "" : ls.locale.getLocaleId().getId();
        }
        Span name = new Span(display);
        name.addClassNames(LumoUtility.FontSize.MEDIUM, LumoUtility.FontWeight.MEDIUM);
        Span code = new Span(ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId());
        code.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        left.add(name, code);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        Span pct = new Span(String.format("%.1f%%", ls.translatedPct));
        pct.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        pct.getStyle().set("color", "var(--aura-green)");
        Span tag = new Span("Translated");
        tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
        right.add(pct, tag);

        row.add(left, right);
        card.add(row);

        ProgressBar bar = new ProgressBar(0.0, 1.0,
                Math.max(0.0, Math.min(1.0, ls.translatedPct / 100.0)));
        bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
        bar.getStyle().set("margin-top", "0.4rem");
        card.add(bar);
        return card;
    }

    private Div buildDocumentsPanel(List<HDocument> documents) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");

        Grid<HDocument> grid = new Grid<>(HDocument.class, false);
        grid.addColumn(HDocument::getDocId).setHeader("Doc Id").setAutoWidth(true);
        grid.addColumn(d -> d.getPath() == null ? "" : d.getPath())
                .setHeader("Path").setAutoWidth(true);
        grid.addColumn(d -> d.getContentType() == null ? "" : d.getContentType().toString())
                .setHeader("Content type").setAutoWidth(true);
        grid.addColumn(d -> d.getLocale() == null || d.getLocale().getLocaleId() == null
                ? "" : d.getLocale().getLocaleId().getId())
                .setHeader("Lang").setAutoWidth(true);
        grid.setItems(documents);
        grid.setAllRowsVisible(true);
        panel.add(grid);
        return panel;
    }
}
