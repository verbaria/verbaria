package org.zanata.spring.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.zanata.model.HIterationGroup;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.PersonRepository;
import org.vaadin.lineawesome.LineAwesomeIcon;
import org.zanata.spring.repository.ProjectRepository;
import org.zanata.spring.vaadin.project.ProjectView;

@AnonymousAllowed
@Route(value = "explore", layout = MainLayout.class)
public class ExploreView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.explore"; }


    private static final int PAGE_SIZE = 10;

    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final PersonRepository personRepository;
    private final IterationGroupRepository groupRepository;

    private final Section<HProject> projectsSection;
    private final Section<HIterationGroup> groupsSection;

    private String currentQuery = "";

    public ExploreView(ProjectRepository projectRepository,
                       LocaleRepository localeRepository,
                       PersonRepository personRepository,
                       IterationGroupRepository groupRepository) {
        this.projectRepository = projectRepository;
        this.localeRepository = localeRepository;
        this.personRepository = personRepository;
        this.groupRepository = groupRepository;

        setSizeFull();
        setPadding(true);
        setAlignItems(FlexComponent.Alignment.CENTER);

        TextField search = new TextField();
        search.setPlaceholder(getTranslation("explore.searchPlaceholder"));
        search.setWidth("520px");
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(300);
        search.setPrefixComponent(LineAwesomeIcon.SEARCH_SOLID.create());
        search.addValueChangeListener(e -> {
            currentQuery = e.getValue() == null ? "" : e.getValue().trim();
            reload();
        });

        HorizontalLayout searchBar = new HorizontalLayout(search);
        searchBar.setAlignItems(FlexComponent.Alignment.CENTER);
        searchBar.setSpacing(true);
        searchBar.setWidth("520px");
        add(searchBar);

        Div sections = new Div();
        sections.setWidth("760px");
        sections.getStyle().set("max-width", "100%");

        projectsSection = new Section<>(getTranslation("explore.section.projects"), LineAwesomeIcon.FOLDER,
                p -> projectRow(p));
        groupsSection = new Section<>(getTranslation("explore.section.groups"), LineAwesomeIcon.FOLDER,
                g -> groupRow(g));

        sections.add(projectsSection, groupsSection);
        add(sections);

        reload();
    }

    private void reload() {
        projectsSection.load(p -> projectRepository.search(currentQuery, p));
        groupsSection.load(p -> groupRepository.search(currentQuery, p));
    }

    private Component projectRow(HProject p) {
        RouterLink title = new RouterLink(p.getSlug(), ProjectView.class,
                new com.vaadin.flow.router.RouteParameters("slug", p.getSlug()));
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("display", "block");
        Span desc = new Span(p.getName() == null ? "" : getTranslation("explore.projectPrefix", p.getName()));
        desc.getStyle().set("font-style", "italic");
        desc.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Div d = new Div(title, desc);
        d.getStyle().set("padding", "0.4rem 0");
        return d;
    }

    private Component groupRow(HIterationGroup g) {
        Anchor title = new Anchor("/version-group/version_group/" + g.getSlug(),
                g.getName() == null ? g.getSlug() : g.getName());
        title.getStyle().set("font-weight", "600");
        title.getStyle().set("display", "block");
        Span desc = new Span(g.getDescription() == null ? "" : g.getDescription());
        desc.getStyle().set("font-style", "italic");
        desc.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Div d = new Div(title, desc);
        d.getStyle().set("padding", "0.4rem 0");
        return d;
    }

    private Component personRow(HPerson p) {
        Span name = new Span(p.getName() == null ? getTranslation("explore.unnamed") : p.getName());
        name.getStyle().set("font-weight", "600");
        name.getStyle().set("display", "block");
        Span email = new Span(p.getEmail() == null ? "" : p.getEmail());
        email.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Div d = new Div(name, email);
        d.getStyle().set("padding", "0.4rem 0");
        return d;
    }

    private Component languageRow(HLocale l) {
        Span name = new Span(l.getDisplayName() == null
                ? l.getLocaleId().getId() : l.getDisplayName());
        name.getStyle().set("font-weight", "600");
        name.getStyle().set("display", "block");
        Span code = new Span(l.getLocaleId() == null ? "" : l.getLocaleId().getId());
        code.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);
        Div d = new Div(name, code);
        d.getStyle().set("padding", "0.4rem 0");
        return d;
    }

    private static class Section<T> extends Div {
        private final String title;
        private final LineAwesomeIcon icon;
        private final Function<T, Component> renderer;
        private final HorizontalLayout header = new HorizontalLayout();
        private final Span countSpan = new Span("0");
        private final Span pageInfo = new Span("");
        private final Button prev = new Button(LineAwesomeIcon.ANGLE_LEFT_SOLID.create());
        private final Button next = new Button(LineAwesomeIcon.ANGLE_RIGHT_SOLID.create());
        private final Div list = new Div();
        private int currentPage = 0;
        private int totalPages = 0;
        private java.util.function.Function<PageRequest, Page<T>> loader;

        Section(String title, LineAwesomeIcon icon, Function<T, Component> renderer) {
            this.title = title;
            this.icon = icon;
            this.renderer = renderer;

            getStyle().set("margin-top", "1.5rem");

            var ico = icon.create();
            ico.getStyle().set("color", "var(--vaadin-text-color-secondary)");
            H4 heading = new H4(title);
            heading.addClassNames(LumoUtility.Margin.NONE, LumoUtility.FontSize.SMALL,
                    LumoUtility.TextColor.SECONDARY);
            heading.getStyle().set("letter-spacing", "0.1em");

            countSpan.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

            prev.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            next.addThemeVariants(ButtonVariant.TERTIARY, ButtonVariant.SMALL);
            prev.addClickListener(e -> { if (currentPage > 0) { currentPage--; reloadPage(); } });
            next.addClickListener(e -> { if (currentPage + 1 < totalPages) { currentPage++; reloadPage(); } });

            pageInfo.addClassNames(LumoUtility.FontSize.SMALL, LumoUtility.TextColor.SECONDARY);

            HorizontalLayout left = new HorizontalLayout(ico, heading, countSpan);
            left.setAlignItems(FlexComponent.Alignment.CENTER);
            left.setSpacing(true);
            HorizontalLayout right = new HorizontalLayout(prev, pageInfo, next);
            right.setAlignItems(FlexComponent.Alignment.CENTER);
            right.setSpacing(false);

            header.setWidthFull();
            header.setAlignItems(FlexComponent.Alignment.CENTER);
            header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
            header.add(left, right);
            header.getStyle().set("border-bottom", "1px solid var(--vaadin-border-color)");
            header.getStyle().set("padding-bottom", "0.5rem");

            add(header, list);
        }

        void load(java.util.function.Function<PageRequest, Page<T>> loader) {
            this.loader = loader;
            this.currentPage = 0;
            reloadPage();
        }

        private void reloadPage() {
            Page<T> page = loader.apply(PageRequest.of(currentPage, PAGE_SIZE));
            totalPages = Math.max(1, page.getTotalPages());
            countSpan.setText(String.valueOf(page.getTotalElements()));
            pageInfo.setText(getTranslation("explore.pageInfo", (currentPage + 1), totalPages));
            prev.setEnabled(currentPage > 0);
            next.setEnabled(currentPage + 1 < totalPages);
            list.removeAll();
            List<T> items = page.getContent();
            if (items.isEmpty()) {
                Paragraph empty = new Paragraph(getTranslation("explore.noResults"));
                empty.addClassNames(LumoUtility.TextColor.SECONDARY, LumoUtility.FontSize.SMALL);
                list.add(empty);
            } else {
                items.forEach(item -> list.add(renderer.apply(item)));
            }
        }
    }
}
