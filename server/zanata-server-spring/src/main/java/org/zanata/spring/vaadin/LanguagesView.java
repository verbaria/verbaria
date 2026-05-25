package org.zanata.spring.vaadin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.zanata.model.HLocale;
import org.zanata.spring.repository.LocaleRepository;

@AnonymousAllowed
@Route(value = "languages", layout = MainLayout.class)
public class LanguagesView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.languages"; }


    public LanguagesView(LocaleRepository localeRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2(getTranslation("languages.title")));

        Grid<HLocale> grid = new Grid<>(HLocale.class, false);
        grid.addColumn(l -> l.getLocaleId() == null ? "" : l.getLocaleId().getId())
                .setHeader(getTranslation("languages.colLocale")).setSortable(true).setAutoWidth(true);
        grid.addColumn(HLocale::getDisplayName).setHeader(getTranslation("languages.colDisplayName")).setAutoWidth(true);
        grid.addColumn(HLocale::getNativeName).setHeader(getTranslation("languages.colNativeName")).setAutoWidth(true);
        grid.addColumn(l -> l.isActive() ? getTranslation("languages.statusActive") : getTranslation("languages.statusDisabled"))
                .setHeader(getTranslation("languages.colStatus")).setAutoWidth(true);
        grid.addColumn(l -> l.isEnabledByDefault() ? getTranslation("languages.defaultYes") : getTranslation("languages.defaultNo"))
                .setHeader(getTranslation("languages.colDefault")).setAutoWidth(true);

        grid.setItems(localeRepository.findAll());
        grid.setSizeFull();
        grid.addItemClickListener(e -> {
            HLocale l = e.getItem();
            if (l != null && l.getLocaleId() != null) {
                getUI().ifPresent(ui -> ui.navigate(
                        "language/" + l.getLocaleId().getId()));
            }
        });

        add(grid);
    }
}
