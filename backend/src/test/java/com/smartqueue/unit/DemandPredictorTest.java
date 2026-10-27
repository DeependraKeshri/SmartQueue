package com.smartqueue.unit;

import com.smartqueue.dto.InsightDtos.DemandForecastDto;
import com.smartqueue.intelligence.DemandInput;
import com.smartqueue.intelligence.StatisticalDemandPredictor;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DemandPredictorTest {
    private final StatisticalDemandPredictor predictor = new StatisticalDemandPredictor();

    private List<DemandInput.DayHistory> weeklyHistory(LocalDate target, int weeks, int perHour) {
        List<DemandInput.DayHistory> h = new ArrayList<>();
        for (int i = 1; i <= weeks; i++) {
            int[] hours = new int[24];
            hours[10] = perHour;
            hours[11] = perHour;
            h.add(new DemandInput.DayHistory(target.minusWeeks(i), hours));
        }
        return h;
    }

    @Test
    void refusesToForecastWithoutEnoughHistory() {
        LocalDate t = LocalDate.of(2026, 1, 12);
        DemandForecastDto r = predictor.predict(new DemandInput(t, weeklyHistory(t, 2, 20), 600, 1, 2, 2, List.of(10, 11)));
        assertFalse(r.sufficientData());
        assertTrue(r.hours().isEmpty());
        assertNotNull(r.message());
    }

    @Test
    void forecastsHighDemandAndRecommendsMoreCounters() {
        LocalDate t = LocalDate.of(2026, 1, 12);
        // 20 arrivals/hour, 10-min service => 1 counter handles 6/hour; demand far above capacity
        DemandForecastDto r = predictor.predict(new DemandInput(t, weeklyHistory(t, 6, 20), 600, 1, 4, 2, List.of(10, 11)));
        assertTrue(r.sufficientData());
        assertEquals("WEEKDAY_WEIGHTED_AVERAGE", r.method());
        assertEquals(20.0, r.hours().get(0).expectedArrivals(), 0.01);
        assertTrue(r.hours().get(0).utilizationPercent() > 100);
        assertTrue(r.hours().get(0).recommendedCounters() >= 4);
        assertFalse(r.recommendations().isEmpty());
        assertTrue(r.recommendations().stream().anyMatch(x -> x.type().equals("COUNTER")));
        assertTrue(r.recommendations().stream().anyMatch(x -> x.type().equals("STAFF")));
    }

    @Test
    void lowDemandSuggestsFewerCounters() {
        LocalDate t = LocalDate.of(2026, 1, 12);
        DemandForecastDto r = predictor.predict(new DemandInput(t, weeklyHistory(t, 6, 1), 300, 3, 3, 3, List.of(10, 11)));
        assertTrue(r.recommendations().stream().anyMatch(x -> x.type().equals("INFO")));
    }

    @Test
    void fallsBackToRecentDaysWhenWeekdayHistoryIsThin() {
        LocalDate t = LocalDate.of(2026, 1, 12);
        List<DemandInput.DayHistory> h = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            int[] hours = new int[24];
            hours[10] = 5;
            h.add(new DemandInput.DayHistory(t.minusDays(i), hours));   // different weekdays
        }
        DemandForecastDto r = predictor.predict(new DemandInput(t, h, 600, 1, 1, 1, List.of(10)));
        assertTrue(r.sufficientData());
        assertEquals("RECENT_DAYS_AVERAGE", r.method());
        assertNotNull(r.message());
    }
}
