package org.zanata.spring.vaadin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.zanata.model.HIterationGroup;
import org.zanata.model.HLocale;
import org.zanata.model.HPerson;
import org.zanata.model.HProject;
import org.zanata.spring.repository.IterationGroupRepository;
import org.zanata.spring.repository.LocaleRepository;
import org.zanata.spring.repository.PersonRepository;
import org.zanata.spring.repository.ProjectRepository;

@AnonymousAllowed
@Route(value = "explore", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Explore | Zanata")
public class ExploreView extends VerticalLayout {

    private static final int PAGE_SIZE = 25;

    private final ProjectRepository projectRepository;
    private final LocaleRepository localeRepository;
    private final PersonRepository personRepository;
    private final IterationGroupRepository groupRepository;

    private final Grid<HProject> projectGrid = new Grid<>(HProject.class, false);
    private final Grid<HLocale>  languageGrid = new Grid<>(HLocale.class, false);
    private final Grid<HPerson>  personGrid = new Grid<>(HPerson.class, false);
    private final Grid<HIterationGroup> groupGrid = new Grid<>(HIterationGroup.class, false);

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
        setSpacing(true);

        H2 heading = new H2("Explore");

        TextField search = new TextField();
        search.setPlaceholder("Search projects, languages, people, groups");
        search.setClearButtonVisible(true);
        search.setWidth("480px");
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setValueChangeTimeout(300);
        search.addValueChangeListener(e -> reload(e.getValue() == null ? "" : e.getValue().trim()));

        configureProjectGrid();
        configureLanguageGrid();
        configurePersonGrid();
        configureGroupGrid();

        add(heading, search,
                new H3("Projects"), projectGrid,
                new H3("Languages"), languageGrid,
                new H3("People"), personGrid,
                new H3("Groups"), groupGrid);

        reload("");
    }

    private void configureProjectGrid() {
        projectGrid.addColumn(HProject::getSlug).setHeader("Slug").setAutoWidth(true);
        projectGrid.addColumn(HProject::getName).setHeader("Name").setAutoWidth(true);
        projectGrid.addColumn(HProject::getDescription).setHeader("Description").setAutoWidth(true);
        projectGrid.setHeight("260px");
        projectGrid.addItemClickListener(e -> {
            HProject p = e.getItem();
            if (p != null) {
                getUI().ifPresent(ui -> ui.navigate("project/view/" + p.getSlug()));
            }
        });
    }

    private void configureLanguageGrid() {
        languageGrid.addColumn(l -> l.getLocaleId() == null ? "" : l.getLocaleId().getId())
                .setHeader("Locale").setAutoWidth(true);
        languageGrid.addColumn(HLocale::getDisplayName).setHeader("Display name").setAutoWidth(true);
        languageGrid.addColumn(HLocale::getNativeName).setHeader("Native name").setAutoWidth(true);
        languageGrid.setHeight("260px");
    }

    private void configurePersonGrid() {
        personGrid.addColumn(HPerson::getName).setHeader("Name").setAutoWidth(true);
        personGrid.addColumn(HPerson::getEmail).setHeader("Email").setAutoWidth(true);
        personGrid.setHeight("260px");
    }

    private void configureGroupGrid() {
        groupGrid.addColumn(HIterationGroup::getSlug).setHeader("Slug").setAutoWidth(true);
        groupGrid.addColumn(HIterationGroup::getName).setHeader("Name").setAutoWidth(true);
        groupGrid.addColumn(HIterationGroup::getDescription).setHeader("Description").setAutoWidth(true);
        groupGrid.setHeight("260px");
    }

    private void reload(String q) {
        var pageable = PageRequest.of(0, PAGE_SIZE);
        projectGrid.setItems(projectRepository.search(q == null ? "" : q, pageable).getContent());
        groupGrid.setItems(groupRepository.search(q == null ? "" : q, pageable).getContent());
        personGrid.setItems(personRepository.search(q == null ? "" : q, pageable).getContent());
        languageGrid.setItems(localeRepository.search(q == null ? "" : q, pageable).getContent());
    }
}
