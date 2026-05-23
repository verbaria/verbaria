package org.zanata.spring.web.rest;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * /rest/stats — replaces the legacy StatisticsService for the bits the
 * React profile screen needs.  Returns empty datasets until activity
 * tracking is hooked up.
 */
@RestController
@RequestMapping("/rest/stats")
public class StatsController {

    @GetMapping("/user/{username}/{range}")
    public List<UserStats> userStats(
            @PathVariable("username") String username,
            @PathVariable("range") String range) {
        // range is "fromDate..toDate"; ignore for the empty-day-stub path.
        return List.of();
    }

    public record UserStats(
            String date,
            String savedState,
            long wordCount) {}
}
