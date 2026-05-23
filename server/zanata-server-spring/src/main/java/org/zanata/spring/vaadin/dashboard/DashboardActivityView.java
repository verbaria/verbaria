package org.zanata.spring.vaadin.dashboard;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

import org.zanata.model.Activity;
import org.zanata.spring.repository.ActivityRepository;
import org.zanata.spring.vaadin.MainLayout;

@Route(value = "dashboard/activity", layout = MainLayout.class)
@PageTitle("Activity | Zanata")
@AnonymousAllowed
public class DashboardActivityView extends VerticalLayout {

    public DashboardActivityView(ActivityRepository activityRepository) {
        setSizeFull();
        setPadding(true);

        H2 heading = new H2("Activity");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<Activity> activities;
        if (auth != null && auth.getName() != null) {
            activities = activityRepository.findByActor(auth.getName(), PageRequest.of(0, 50));
        } else {
            activities = Collections.emptyList();
        }

        Grid<Activity> grid = new Grid<>(Activity.class, false);
        grid.addColumn(a -> a.getActivityType() == null ? "" : a.getActivityType().name())
                .setHeader("Type").setAutoWidth(true);
        grid.addColumn(Activity::getApproxTime).setHeader("When").setAutoWidth(true);
        grid.addColumn(Activity::getWordCount).setHeader("Words").setAutoWidth(true);
        grid.setItems(activities);

        add(heading, grid);
    }
}
