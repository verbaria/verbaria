package org.zanata.spring.vaadin.iteration;

import java.util.List;

import com.vaadin.componentfactory.Breadcrumb;
import com.vaadin.componentfactory.Breadcrumbs;
import com.vaadin.flow.component.UI;
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
    private String currentSourceLocaleId = "en-US";

    private final org.zanata.spring.service.SourceUploadService sourceUploadService;
    private final org.zanata.spring.repository.AccountRepository accountRepository;

    public IterationView(ProjectIterationRepository iterationRepository,
                         DocumentRepository documentRepository,
                         TextFlowTargetRepository targetRepository,
                         LocaleRepository localeRepository,
                         org.zanata.spring.service.SourceUploadService sourceUploadService,
                         org.zanata.spring.repository.AccountRepository accountRepository) {
        this.iterationRepository = iterationRepository;
        this.documentRepository = documentRepository;
        this.targetRepository = targetRepository;
        this.localeRepository = localeRepository;
        this.sourceUploadService = sourceUploadService;
        this.accountRepository = accountRepository;
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
        if (iteration.getProject() != null
                && iteration.getProject().getDefaultSourceLocale() != null
                && iteration.getProject().getDefaultSourceLocale().getLocaleId() != null) {
            this.currentSourceLocaleId =
                    iteration.getProject().getDefaultSourceLocale().getLocaleId().getId();
        }

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

    private Breadcrumbs buildBreadcrumb(String projectSlug) {
        Breadcrumbs crumbs = new Breadcrumbs();
        crumbs.add(
                new Breadcrumb("Home", "/"),
                new Breadcrumb("Projects", "/explore"),
                new Breadcrumb(projectSlug, "/project/view/" + projectSlug, true)
        );
        return crumbs;
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
        panel.getStyle().set("box-sizing", "border-box");
        panel.getStyle().set("overflow-x", "hidden");

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

        Span counter = new Span(stats.perLocale.size() + " languages");
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
        // Source locale always first, then everything else by the chosen sort.
        java.util.Comparator<IterationStats.LocaleStats> finalCmp =
                java.util.Comparator.<IterationStats.LocaleStats, Integer>comparing(
                        ls -> currentSourceLocaleId.equalsIgnoreCase(
                                ls.locale.getLocaleId() == null ? ""
                                        : ls.locale.getLocaleId().getId())
                                ? 0 : 1)
                        .thenComparing(cmp);
        rows.stream().sorted(finalCmp).forEach(ls -> container.add(buildLocaleRow(ls)));
    }

    private Div buildLocaleRow(IterationStats.LocaleStats ls) {
        Div card = new Div();
        card.addClassNames("zanata-locale-row",
                LumoUtility.Border.BOTTOM, LumoUtility.BorderColor.CONTRAST_10);
        card.getStyle().set("width", "100%");
        card.getStyle().set("box-sizing", "border-box");
        card.getStyle().set("cursor", "pointer");
        card.getStyle().set("padding", "0.85rem 1rem");
        card.getStyle().set("overflow", "hidden");
        card.getStyle().set("transition", "background-color 100ms ease-out");
        // Hover state — matches the legacy row interaction.
        card.getElement().executeJs(
                "this.addEventListener('mouseenter', () => "
                        + "this.style.backgroundColor='var(--vaadin-background-container)');"
                        + "this.addEventListener('mouseleave', () => "
                        + "this.style.backgroundColor='');");
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
        name.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
        // Aura ships --aura-blue-text (light/dark aware); fall back to the
        // Lumo primary token if the theme ever changes.
        name.getStyle().set("color",
                "var(--aura-blue-text, var(--lumo-primary-text-color))");
        Span code = new Span(ls.locale.getLocaleId() == null
                ? "" : ls.locale.getLocaleId().getId());
        code.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        left.add(name, code);

        VerticalLayout right = new VerticalLayout();
        right.setPadding(false);
        right.setSpacing(false);
        right.setAlignItems(FlexComponent.Alignment.END);
        boolean isSource = currentSourceLocaleId.equalsIgnoreCase(localeIdStr);
        if (isSource) {
            Span src = new Span("Source");
            src.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.FontWeight.BOLD);
            src.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            src.getStyle().set("line-height", "1.1");
            Span tag = new Span("Primary contribution");
            tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
            right.add(src, tag);
        } else {
            Span pct = new Span(String.format("%.2f%%", ls.translatedPct));
            pct.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.FontWeight.BOLD);
            pct.getStyle().set("color", "var(--aura-green)");
            pct.getStyle().set("line-height", "1.1");
            Span tag = new Span("Translated");
            tag.addClassNames(LumoUtility.FontSize.XSMALL, LumoUtility.TextColor.SECONDARY);
            right.add(pct, tag);
        }

        // Download / export actions tucked behind a single overflow (⋯) menu
        // so the row's width doesn't grow. The legacy UI did the same — one
        // dropdown in the top-right of the panel; here we put it per-row so
        // each locale's bundle is one click away. Hidden for the source
        // locale (en-US has nothing to download).
        com.vaadin.flow.component.menubar.MenuBar overflow = null;
        if (!localeIdStr.isBlank()
                && !currentSourceLocaleId.equalsIgnoreCase(localeIdStr)
                && currentProjectSlug != null && currentVersionSlug != null) {
            overflow = new com.vaadin.flow.component.menubar.MenuBar();
            overflow.addThemeVariants(
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_TERTIARY_INLINE,
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_SMALL,
                    com.vaadin.flow.component.menubar.MenuBarVariant.LUMO_ICON);
            com.vaadin.flow.component.contextmenu.MenuItem trigger =
                    overflow.addItem(LineAwesomeIcon.ELLIPSIS_H_SOLID.create());
            trigger.getElement().setAttribute("aria-label", "Downloads");
            trigger.getElement().setAttribute("title", "Downloads");

            String offlineHref = "/rest/file/translation/"
                    + currentProjectSlug + "/" + currentVersionSlug + "/"
                    + localeIdStr + "/offlinepo";
            String tmxHref = "/rest/tm/projects/" + currentProjectSlug
                    + "/iterations/" + currentVersionSlug
                    + "?locale=" + localeIdStr;
            trigger.getSubMenu().addItem("Download for offline translation",
                    e -> UI.getCurrent().getPage().open(offlineHref, "_self"));
            trigger.getSubMenu().addItem("Export TMX",
                    e -> UI.getCurrent().getPage().open(tmxHref, "_self"));
            // Stop the card's click listener from firing when the menu is clicked.
            overflow.getElement().addEventListener("click", e -> {})
                    .addEventData("event.stopPropagation()");
        }

        HorizontalLayout rightCol = new HorizontalLayout(right);
        if (overflow != null) {
            rightCol.add(overflow);
        }
        rightCol.setAlignItems(FlexComponent.Alignment.CENTER);
        rightCol.setSpacing(true);
        rightCol.getStyle().set("flex-shrink", "0");

        left.getStyle().set("min-width", "0");
        left.getStyle().set("flex", "1 1 auto");

        row.add(left, rightCol);
        row.setFlexGrow(1, left);
        row.setFlexGrow(0, rightCol);
        card.add(row);

        if (!isSource) {
            ProgressBar bar = new ProgressBar(0.0, 1.0,
                    Math.max(0.0, Math.min(1.0, ls.translatedPct / 100.0)));
            bar.getStyle().set("--vaadin-color-primary", "var(--aura-green)");
            bar.getStyle().set("margin-top", "0.4rem");
            card.add(bar);
        }
        return card;
    }

    private Div buildDocumentsPanel(List<HDocument> documents) {
        Div panel = new Div();
        panel.addClassNames(LumoUtility.Border.ALL, LumoUtility.BorderColor.CONTRAST_10,
                LumoUtility.BorderRadius.MEDIUM, LumoUtility.Padding.MEDIUM);
        panel.getStyle().set("width", "100%");
        panel.getStyle().set("box-sizing", "border-box");

        if (canUpload()) {
            panel.add(buildUploader());
        }

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

    private com.vaadin.flow.component.html.Div buildUploader() {
        var buffer = new com.vaadin.flow.component.upload.receivers.MultiFileMemoryBuffer();
        var upload = new com.vaadin.flow.component.upload.Upload(buffer);
        upload.setAcceptedFileTypes(".properties", ".po", ".pot", ".xlf", ".xliff");
        upload.setDropLabel(new com.vaadin.flow.component.html.Span(
                "Drop a .properties / .po / .xlf file here, or click to upload"));
        upload.addSucceededListener(e -> {
            String filename = e.getFileName();
            var actor = currentAccount();
            if (actor == null) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Sign in to upload documents", 3000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
                return;
            }
            try (java.io.InputStream in = buffer.getInputStream(filename)) {
                var result = sourceUploadService.upload(
                        currentProjectSlug, currentVersionSlug, filename, in, actor);
                com.vaadin.flow.component.notification.Notification.show(
                        (result.created() ? "Created" : "Updated") + " "
                                + result.document().getDocId(),
                        2500,
                        com.vaadin.flow.component.notification.Notification.Position.BOTTOM_START);
                UI.getCurrent().getPage().reload();
            } catch (Exception ex) {
                com.vaadin.flow.component.notification.Notification.show(
                        "Upload failed: " + ex.getMessage(), 5000,
                        com.vaadin.flow.component.notification.Notification.Position.MIDDLE);
            }
        });
        com.vaadin.flow.component.html.Div wrap = new com.vaadin.flow.component.html.Div(upload);
        wrap.getStyle().set("margin-bottom", "0.75rem");
        return wrap;
    }

    private boolean canUpload() {
        return currentAccount() != null;
    }

    private org.zanata.model.HAccount currentAccount() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            return null;
        }
        return accountRepository.findByUsername(auth.getName()).orElse(null);
    }
}
