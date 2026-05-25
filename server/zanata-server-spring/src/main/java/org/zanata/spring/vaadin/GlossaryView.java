package org.zanata.spring.vaadin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import org.springframework.data.domain.PageRequest;
import org.zanata.model.HGlossaryEntry;
import org.zanata.spring.repository.GlossaryEntryRepository;

@AnonymousAllowed
@Route(value = "glossary", layout = MainLayout.class)
@PageTitle("Glossary | Zanata")
public class GlossaryView extends VerticalLayout {

    public GlossaryView(GlossaryEntryRepository glossaryRepository) {
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("Glossary"));

        Grid<HGlossaryEntry> grid = new Grid<>(HGlossaryEntry.class, false);
        grid.addColumn(e -> e.getSrcLocale() == null || e.getSrcLocale().getLocaleId() == null
                ? "" : e.getSrcLocale().getLocaleId().getId())
                .setHeader("Source locale").setAutoWidth(true);
        grid.addColumn(e -> {
            var srcTerm = e.getGlossaryTerms().get(e.getSrcLocale());
            return srcTerm == null ? "" : srcTerm.getContent();
        }).setHeader("Source term").setAutoWidth(true);
        grid.addColumn(HGlossaryEntry::getPos).setHeader("Part of speech").setAutoWidth(true);
        grid.addColumn(HGlossaryEntry::getDescription).setHeader("Description").setAutoWidth(true);
        grid.addColumn(e -> e.getGlossaryTerms() == null ? 0 : e.getGlossaryTerms().size() - 1)
                .setHeader("Translations").setAutoWidth(true);

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
