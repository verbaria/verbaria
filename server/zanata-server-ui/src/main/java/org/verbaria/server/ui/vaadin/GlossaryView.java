package org.verbaria.server.ui.vaadin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.Route;
import org.verbaria.server.ui.vaadin.i18n.TitleKey;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.zanata.model.HGlossaryEntry;
import org.verbaria.server.headless.repository.GlossaryEntryRepository;

@AnonymousAllowed
@Route(value = "glossary", layout = MainLayout.class)
public class GlossaryView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.glossary"; }


    public GlossaryView(GlossaryEntryRepository glossaryRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2(getTranslation("glossary.title")));

        Grid<HGlossaryEntry> grid = new Grid<>(HGlossaryEntry.class, false);
        grid.addColumn(e -> e.getSrcLocale() == null || e.getSrcLocale().getLocaleId() == null
                ? "" : e.getSrcLocale().getLocaleId().getId())
                .setHeader(getTranslation("glossary.colSourceLocale")).setAutoWidth(true);
        grid.addColumn(e -> {
            var srcTerm = e.getGlossaryTerms().get(e.getSrcLocale());
            return srcTerm == null ? "" : srcTerm.getContent();
        }).setHeader(getTranslation("glossary.colSourceTerm")).setAutoWidth(true);
        grid.addColumn(HGlossaryEntry::getPos).setHeader(getTranslation("glossary.colPos")).setAutoWidth(true);
        grid.addColumn(HGlossaryEntry::getDescription).setHeader(getTranslation("glossary.colDescription")).setAutoWidth(true);
        grid.addColumn(e -> e.getGlossaryTerms() == null ? 0 : e.getGlossaryTerms().size() - 1)
                .setHeader(getTranslation("glossary.colTranslations")).setAutoWidth(true);

        // Server-paged DataProvider — glossaries with thousands of terms
        // would otherwise eat the page render.
        CallbackDataProvider<HGlossaryEntry, Void> dp = DataProvider.fromCallbacks(
                q -> {
                    int page = q.getOffset() / Math.max(1, q.getLimit());
                    return glossaryRepository
                            .findAll(PageRequest.of(page, q.getLimit()))
                            .stream();
                },
                q -> (int) Math.min(Integer.MAX_VALUE, glossaryRepository.count()));
        grid.setDataProvider(dp);
        grid.setSizeFull();

        add(grid);
    }
}
