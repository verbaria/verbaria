package org.zanata.spring.web.rest;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zanata.common.ActivityType;
import org.zanata.common.ContentState;
import org.zanata.model.Activity;
import org.zanata.spring.repository.ActivityRepository;

/**
 * /rest/stats endpoints for the React profile screen.
 */
@RestController
@RequestMapping("/rest/stats")
public class StatsController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ActivityRepository activityRepository;

    public StatsController(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @GetMapping("/user/{username}/{range}")
    @Transactional(readOnly = true)
    public List<UserStats> userStats(
            @PathVariable("username") String username,
            @PathVariable("range") String range) {
        // range is "yyyy-MM-dd..yyyy-MM-dd"
        String[] parts = range == null ? new String[0] : range.split("\\.\\.");
        if (parts.length != 2) return List.of();
        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(parts[0], ISO);
            toDate = LocalDate.parse(parts[1], ISO);
        } catch (Exception e) {
            return List.of();
        }
        Date from = Date.from(fromDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date to = Date.from(toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<Activity> rows = activityRepository.findByActorAndRange(username, from, to);
        Map<String, UserStats> agg = new LinkedHashMap<>();
        for (Activity a : rows) {
            ContentState state = stateFor(a.getActivityType());
            if (state == null) continue;
            String day = a.getApproxTime().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate().format(ISO);
            String key = day + "|" + state.name();
            UserStats prior = agg.get(key);
            long add = a.getWordCount();
            agg.put(key, prior == null
                    ? new UserStats(day, state.name(), add)
                    : new UserStats(day, state.name(), prior.wordCount() + add));
        }
        return new ArrayList<>(agg.values());
    }

    private static ContentState stateFor(ActivityType t) {
        if (t == null) return null;
        return switch (t) {
            case UPDATE_TRANSLATION -> ContentState.Translated;
            case REVIEWED_TRANSLATION -> ContentState.Approved;
            default -> null;
        };
    }

    public record UserStats(
            String date,
            String savedState,
            long wordCount) {}
}
