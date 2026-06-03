package org.verbaria.server.ui.vaadin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.treegrid.TreeGrid;
import com.vaadin.flow.data.provider.hierarchy.TreeData;
import com.vaadin.flow.data.provider.hierarchy.TreeDataProvider;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.model.HProject;
import org.verbaria.server.headless.repository.ProjectRepository;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import org.verbaria.server.ui.vaadin.project.ProjectView;
import org.verbaria.server.headless.stats.ProjectStatsCache;
import org.verbaria.server.ui.vaadin.theme.AuraUtility;
import org.verbaria.server.ui.vaadin.theme.ProgressBars;

@AnonymousAllowed
@Route(value = "explore", layout = MainLayout.class)
public class ExploreView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.explore"; }

    private static final int MAX_RESULTS = 2000;

    private final ProjectRepository projectRepository;
    private final ProjectStatsCache statsCache;
    private final TreeGrid<Node> grid = new TreeGrid<>();

    private String currentQuery = "";

    public ExploreView(ProjectRepository projectRepository,
                       ProjectStatsCache statsCache) {
        this.projectRepository = projectRepository;
        this.statsCache = statsCache;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        TextField search = new TextField();
        search.setPlaceholder(getTranslation("explore.searchPlaceholder"));
        search.setWidthFull();
        search.setMaxWidth("520px");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(300);
        search.setPrefixComponent(LineAwesomeIcon.SEARCH_SOLID.create());
        search.addValueChangeListener(e -> {
            currentQuery = e.getValue() == null ? "" : e.getValue().trim();
            reload();
        });
        add(search);

        grid.addComponentHierarchyColumn(this::nodeCell)
                .setHeader(getTranslation("explore.section.projects"));
        grid.addColumn(new ComponentRenderer<>(this::percentCell))
                .setHeader(getTranslation("explore.col.translated"))
                .setFlexGrow(0)
                .setWidth("110px")
                .setTextAlign(ColumnTextAlign.END);
        grid.setSizeFull();
        grid.addClassName("projects-tree");
        // Whole-row click: a project opens, a group expands/collapses.
        grid.addSelectionListener(e -> e.getFirstSelectedItem().ifPresent(n -> {
            if (n.isGroup()) {
                if (grid.isExpanded(n)) {
                    grid.collapse(n);
                } else {
                    grid.expand(n);
                }
                grid.deselect(n);
            } else {
                getUI().ifPresent(ui -> ui.navigate(ProjectView.class,
                        new RouteParameters("slug", n.project.getSlug())));
            }
        }));
        addAndExpand(grid);

        reload();
    }

    private void reload() {
        List<HProject> projects = projectRepository.search(currentQuery,
                PageRequest.of(0, MAX_RESULTS,
                        Sort.by(Sort.Order.asc("name"), Sort.Order.asc("slug"))))
                .getContent();

        TreeData<Node> data = new TreeData<>();
        Map<String, Node> groups = new HashMap<>();
        for (HProject p : projects) {
            String label = p.getName() == null || p.getName().isBlank()
                    ? p.getSlug() : p.getName();
            String[] parts = label.split("/");
            Node parent = null;
            StringBuilder path = new StringBuilder();
            for (int i = 0; i < parts.length - 1; i++) {
                if (path.length() > 0) {
                    path.append('/');
                }
                path.append(parts[i]);
                String key = path.toString();
                Node group = groups.get(key);
                if (group == null) {
                    group = Node.group(parts[i], key);
                    groups.put(key, group);
                    data.addItem(parent, group);
                }
                parent = group;
            }
            data.addItem(parent, Node.project(p, parts[parts.length - 1]));
        }

        grid.setDataProvider(new TreeDataProvider<>(data));
        grid.expandRecursively(data.getRootItems(), Integer.MAX_VALUE);
    }

    private Component nodeCell(Node n) {
        if (n.isGroup()) {
            Span label = new Span(n.label);
            label.addClassNames(AuraUtility.FontWeight.SEMIBOLD,
                    AuraUtility.TextColor.SECONDARY);
            var icon = LineAwesomeIcon.FOLDER.create();
            icon.addClassNames(AuraUtility.TextColor.SECONDARY);
            HorizontalLayout row = new HorizontalLayout(icon, label);
            row.setSpacing(true);
            row.setPadding(false);
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            return row;
        }
        HProject p = n.project;
        // Plain text (no link styling) — the row navigates on selection. Kept on
        // a single line like the group rows so the heights match.
        Span name = new Span(n.label);
        name.addClassNames(AuraUtility.FontWeight.SEMIBOLD);
        HorizontalLayout row = new HorizontalLayout(name);
        row.setSpacing(true);
        row.setPadding(false);
        row.setAlignItems(FlexComponent.Alignment.BASELINE);
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            Span desc = new Span(p.getDescription());
            desc.addClassNames(AuraUtility.FontSize.SMALL,
                    AuraUtility.TextColor.SECONDARY);
            row.add(desc);
        }
        return row;
    }

    private Component percentCell(Node n) {
        if (n.isGroup()) {
            return new Span();
        }
        double pct = statsCache.translatedPercent(n.project.getSlug());
        Span label = new Span(String.format("%.0f%%", pct));
        label.addClassNames(AuraUtility.FontSize.XSMALL,
                AuraUtility.TextColor.SECONDARY);
        ProgressBar bar = ProgressBars.translated(pct);
        bar.setWidthFull();
        VerticalLayout cell = new VerticalLayout(label, bar);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.setAlignItems(FlexComponent.Alignment.STRETCH);
        cell.addClassNames(AuraUtility.Gap.Row.XSMALL);
        return cell;
    }

    /** A tree node: either a name-path group (project null) or a project leaf. */
    private static final class Node {
        final HProject project;
        final String label;
        final String key;

        private Node(HProject project, String label, String key) {
            this.project = project;
            this.label = label;
            this.key = key;
        }

        static Node group(String label, String path) {
            return new Node(null, label, "g:" + path);
        }

        static Node project(HProject project, String label) {
            return new Node(project, label, "p:" + project.getSlug());
        }

        boolean isGroup() {
            return project == null;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Node other && key.equals(other.key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }
}
