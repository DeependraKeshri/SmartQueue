package com.smartqueue.unit;

import com.smartqueue.analytics.DayAggregator;
import com.smartqueue.analytics.DayResult;
import com.smartqueue.entity.*;
import com.smartqueue.service.QueueViewService;
import com.smartqueue.service.ServiceStatsService;
import com.smartqueue.util.BusinessSchedule;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MiscUnitTest {

    // ---- entry state machine
    @Test
    void entryTransitions() {
        assertTrue(EntryStatus.WAITING.canTransitionTo(EntryStatus.CALLED));
        assertFalse(EntryStatus.WAITING.canTransitionTo(EntryStatus.COMPLETED));
        assertTrue(EntryStatus.CALLED.canTransitionTo(EntryStatus.SERVING));
        assertTrue(EntryStatus.SKIPPED.canTransitionTo(EntryStatus.CALLED));
        assertFalse(EntryStatus.COMPLETED.canTransitionTo(EntryStatus.WAITING));
        assertFalse(EntryStatus.CANCELLED.canTransitionTo(EntryStatus.CALLED));
        assertTrue(EntryStatus.NO_SHOW.isTerminal());
    }

    // ---- service stats
    @Test
    void statsFallBackToConfiguredDurationWithoutHistory() {
        ServiceStatsService.Stats s = ServiceStatsService.compute(600, 0, 0, 0);
        assertEquals(600, s.meanSeconds(), 0.001);
        assertEquals(0, s.samples());
    }

    @Test
    void statsShrinkTowardObservedMeanAsSamplesGrow() {
        ServiceStatsService.Stats few = ServiceStatsService.compute(600, 5, 5 * 300.0, 5 * 300.0 * 300.0);
        ServiceStatsService.Stats many = ServiceStatsService.compute(600, 500, 500 * 300.0, 500 * 300.0 * 300.0);
        assertTrue(few.meanSeconds() > many.meanSeconds());
        assertTrue(many.meanSeconds() < 310);
        assertEquals(0, many.stdDevSeconds(), 0.001);
    }

    // ---- aggregation
    @Test
    void medianHandlesOddEvenAndEmpty() {
        assertEquals(0, DayAggregator.median(List.of()));
        assertEquals(3, DayAggregator.median(List.of(5L, 1L, 3L)));
        assertEquals(3, DayAggregator.median(List.of(1L, 2L, 4L, 5L)));
    }

    @Test
    void dayAggregationCountsStatusesAndWaits() {
        ZoneId z = ZoneOffset.UTC;
        LocalDate d = LocalDate.of(2026, 1, 5);
        Instant t0 = d.atTime(9, 0).toInstant(ZoneOffset.UTC);
        QueueEntry done = new QueueEntry();
        done.setStatus(EntryStatus.COMPLETED);
        done.setJoinedAt(t0);
        done.setServingStartedAt(t0.plusSeconds(600));
        done.setCompletedAt(t0.plusSeconds(900));
        QueueEntry cancelled = new QueueEntry();
        cancelled.setStatus(EntryStatus.CANCELLED);
        cancelled.setJoinedAt(t0.plusSeconds(3600));
        QueueEntry noShow = new QueueEntry();
        noShow.setStatus(EntryStatus.NO_SHOW);
        noShow.setJoinedAt(t0.plusSeconds(3600));
        DayResult r = DayAggregator.aggregate(d, z, List.of(done, cancelled, noShow), List.of());
        assertEquals(3, r.totalCustomers());
        assertEquals(1, r.completed());
        assertEquals(1, r.cancelled());
        assertEquals(1, r.noShows());
        assertEquals(600, r.sumWaitSeconds());
        assertEquals(300, r.sumServiceSeconds());
        assertEquals(1, r.joinedByHour()[9]);
        assertEquals(2, r.joinedByHour()[10]);
    }

    // ---- display names
    @Test
    void displayNamesRespectPrivacyModes() {
        assertNull(QueueViewService.shownName(DisplayMode.TOKEN_ONLY, "John Doe"));
        assertNull(QueueViewService.shownName(DisplayMode.ANONYMOUS_TOKEN, "John Doe"));
        assertEquals("J*** D***", QueueViewService.shownName(DisplayMode.MASKED_NAME, "John Doe"));
        assertEquals("John D.", QueueViewService.shownName(DisplayMode.FIRST_NAME_INITIAL, "John Doe"));
        assertEquals("Cher", QueueViewService.shownName(DisplayMode.FIRST_NAME_INITIAL, "Cher"));
    }

    // ---- schedule
    private BusinessSchedule schedule() {
        BusinessHours mon = new BusinessHours();
        mon.setDayOfWeek(DayOfWeek.MONDAY);
        mon.setOpenTime(LocalTime.of(9, 0));
        mon.setCloseTime(LocalTime.of(17, 0));
        BusinessHours sun = new BusinessHours();
        sun.setDayOfWeek(DayOfWeek.SUNDAY);
        sun.setClosed(true);
        BreakPeriod lunch = new BreakPeriod();
        lunch.setStartTime(LocalTime.of(12, 0));
        lunch.setEndTime(LocalTime.of(13, 0));
        Holiday h = new Holiday();
        h.setDate(LocalDate.of(2026, 1, 12));
        return new BusinessSchedule(ZoneOffset.UTC, List.of(mon, sun), List.of(lunch), List.of(h));
    }

    @Test
    void scheduleHonoursHoursBreaksHolidaysAndClosedDays() {
        BusinessSchedule s = schedule();
        assertTrue(s.isOpen(Instant.parse("2026-01-05T10:00:00Z")));          // Monday morning
        assertFalse(s.isOpen(Instant.parse("2026-01-05T12:30:00Z")));         // lunch break
        assertFalse(s.isOpen(Instant.parse("2026-01-05T08:59:00Z")));         // before opening
        assertFalse(s.isOpen(Instant.parse("2026-01-05T17:00:00Z")));         // at closing
        assertFalse(s.isOpen(Instant.parse("2026-01-12T10:00:00Z")));         // holiday Monday
        assertFalse(s.isOpen(Instant.parse("2026-01-11T10:00:00Z")));         // Sunday closed
        assertFalse(s.isOpen(Instant.parse("2026-01-06T10:00:00Z")));         // Tuesday not configured
        assertEquals(2, s.openIntervals(LocalDate.of(2026, 1, 5)).size());
        assertEquals(7 * 3600, s.openSeconds(LocalDate.of(2026, 1, 5)));
        assertEquals(Instant.parse("2026-01-05T12:00:00Z"), s.closingTime(Instant.parse("2026-01-05T10:00:00Z")).orElseThrow());
    }

    @Test
    void scheduleRespectsBusinessTimezone() {
        BusinessHours mon = new BusinessHours();
        mon.setDayOfWeek(DayOfWeek.MONDAY);
        mon.setOpenTime(LocalTime.of(9, 0));
        mon.setCloseTime(LocalTime.of(17, 0));
        BusinessSchedule s = new BusinessSchedule(ZoneId.of("Asia/Kolkata"), List.of(mon), List.of(), List.of());
        assertTrue(s.isOpen(Instant.parse("2026-01-05T04:00:00Z")));   // 09:30 IST
        assertTrue(s.isOpen(Instant.parse("2026-01-05T10:00:00Z")));   // 15:30 IST
        assertFalse(s.isOpen(Instant.parse("2026-01-05T12:00:00Z")));  // 17:30 IST closed
    }
}
