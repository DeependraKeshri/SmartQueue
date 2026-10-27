package com.smartqueue.intelligence;

import java.time.LocalDate;
import java.util.List;

public record DemandInput(LocalDate target, List<DayHistory> history, double avgServiceSeconds, int activeCounters,
                          int totalCounters, int staffCount, List<Integer> openHours) {
    /** joinedByHour has 24 entries. */
    public record DayHistory(LocalDate date, int[] joinedByHour) {}
}
