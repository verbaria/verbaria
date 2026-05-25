package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.zanata.spring.i18n.TitleKey;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/cachestats", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class AdminCacheStatsView extends VerticalLayout implements TitleKey {

    @Override public String pageTitleKey() { return "page.cacheStats"; }


    public record CacheRow(String region, long hits, long misses, long puts) {}

    public AdminCacheStatsView() {
        setSizeFull();
        setPadding(true);
        add(new H2(getTranslation("adminCache.heading")));

        Grid<CacheRow> grid = new Grid<>(CacheRow.class, false);
        grid.addColumn(CacheRow::region).setHeader(getTranslation("adminCache.colRegion"));
        grid.addColumn(CacheRow::hits).setHeader(getTranslation("adminCache.colHits"));
        grid.addColumn(CacheRow::misses).setHeader(getTranslation("adminCache.colMisses"));
        grid.addColumn(CacheRow::puts).setHeader(getTranslation("adminCache.colPuts"));
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
