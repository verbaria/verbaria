package org.zanata.spring.vaadin.admin;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.util.Collections;

import org.zanata.spring.vaadin.MainLayout;

@Route(value = "admin/cachestats", layout = MainLayout.class)
@PageTitle("Cache Stats | Zanata")
@RolesAllowed("ADMIN")
public class AdminCacheStatsView extends VerticalLayout {

    public record CacheRow(String region, long hits, long misses, long puts) {}

    public AdminCacheStatsView() {
        setSizeFull();
        setPadding(true);
        add(new H2("Cache Stats"));

        Grid<CacheRow> grid = new Grid<>(CacheRow.class, false);
        grid.addColumn(CacheRow::region).setHeader("Region");
        grid.addColumn(CacheRow::hits).setHeader("Hits");
        grid.addColumn(CacheRow::misses).setHeader("Misses");
        grid.addColumn(CacheRow::puts).setHeader("Puts");
        grid.setItems(Collections.emptyList());

        add(grid);
    }
}
