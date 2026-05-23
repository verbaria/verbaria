package org.zanata.spring.vaadin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.model.HLocale;
import org.zanata.spring.repository.LocaleRepository;

@AnonymousAllowed
@Route(value = "languages", layout = MainLayout.class)
@PageTitle("Languages | Zanata")
public class LanguagesView extends VerticalLayout {

    public LanguagesView(LocaleRepository localeRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("Languages"));

        Grid<HLocale> grid = new Grid<>(HLocale.class, false);
        grid.addColumn(l -> l.getLocaleId() == null ? "" : l.getLocaleId().getId())
                .setHeader("Locale").setSortable(true).setAutoWidth(true);
        grid.addColumn(HLocale::getDisplayName).setHeader("Display name").setAutoWidth(true);
        grid.addColumn(HLocale::getNativeName).setHeader("Native name").setAutoWidth(true);
        grid.addColumn(l -> l.isActive() ? "Active" : "Disabled")
                .setHeader("Status").setAutoWidth(true);
        grid.addColumn(l -> l.isEnabledByDefault() ? "Yes" : "No")
                .setHeader("Default").setAutoWidth(true);

        grid.setItems(localeRepository.findAll());
        grid.setSizeFull();

        add(grid);
    }
}
