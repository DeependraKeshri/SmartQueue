package com.smartqueue.controller;

import com.smartqueue.analytics.AnalyticsService;
import com.smartqueue.dto.AnalyticsDtos.AnalyticsSummary;
import com.smartqueue.dto.InsightDtos.DemandForecastDto;
import com.smartqueue.intelligence.InsightsService;
import java.time.LocalDate;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize(Roles.MANAGER_UP)
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final InsightsService insights;

    public AnalyticsController(AnalyticsService analytics, InsightsService insights) {
        this.analytics = analytics;
        this.insights = insights;
    }

    private static LocalDate orToday(LocalDate d) {
        return d == null ? LocalDate.now() : d;
    }

    @GetMapping("/api/analytics/daily")
    public AnalyticsSummary daily(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analytics.day(CurrentUser.requireBusinessId(), orToday(date));
    }

    @GetMapping("/api/analytics/weekly")
    public AnalyticsSummary weekly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analytics.week(CurrentUser.requireBusinessId(), orToday(date));
    }

    @GetMapping("/api/analytics/monthly")
    public AnalyticsSummary monthly(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return analytics.month(CurrentUser.requireBusinessId(), orToday(date));
    }

    @GetMapping("/api/analytics/range")
    public AnalyticsSummary range(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return analytics.summary(CurrentUser.requireBusinessId(), from, to);
    }

    @GetMapping("/api/insights/demand")
    public DemandForecastDto demand(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return insights.forecast(CurrentUser.requireBusinessId(), date == null ? LocalDate.now().plusDays(1) : date);
    }
}
