package com.smartqueue.dto;

import java.time.LocalDate;
import java.util.List;

public final class InsightDtos {
    private InsightDtos() {}

    public record HourForecast(int hour, double expectedArrivals, double capacityPerHour, double utilizationPercent,
                               double expectedQueueLength, int recommendedCounters) {}

    public record Recommendation(String type, String severity, String message, int fromHour, int toHour) {}

    public record DemandForecastDto(LocalDate date, boolean sufficientData, int historyDays, String method,
                                    String message, int activeCounters, double avgServiceMinutes,
                                    List<HourForecast> hours, List<Recommendation> recommendations) {}
}
