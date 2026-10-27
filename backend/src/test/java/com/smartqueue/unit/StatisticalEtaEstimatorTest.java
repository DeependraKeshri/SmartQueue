package com.smartqueue.unit;

import com.smartqueue.eta.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalEtaEstimatorTest {
    private final StatisticalEtaEstimator estimator = new StatisticalEtaEstimator();
    private final Instant now = Instant.parse("2026-01-05T10:00:00Z");

    private EtaContext ctx(int waiting, int inProgress, int counters, double mean, double sd, long samples, boolean open) {
        List<EtaContext.Waiting> w = new ArrayList<>();
        for (int i = 1; i <= waiting; i++) w.add(new EtaContext.Waiting((long) i, null));
        List<EtaContext.InProgress> p = new ArrayList<>();
        for (int i = 0; i < inProgress; i++) p.add(new EtaContext.InProgress(now));
        return new EtaContext(now, w, p, counters, mean, sd, samples, open, null);
    }

    @Test
    void emptyQueueWithIdleCounterMeansNoWait() {
        EtaResult r = estimator.estimateForNewJoin(ctx(0, 0, 1, 600, 120, 50, true));
        assertEquals(0, r.peopleAhead());
        assertEquals(0, r.estimatedWaitMinutes());
    }

    @Test
    void waitScalesWithPeopleAheadOnOneCounter() {
        EtaResult r = estimator.estimateForNewJoin(ctx(3, 0, 1, 600, 120, 50, true));
        assertEquals(3, r.peopleAhead());
        assertEquals(30, r.estimatedWaitMinutes());
    }

    @Test
    void moreCountersReduceWait() {
        Map<Long, EtaResult> r = estimator.estimateAll(ctx(3, 0, 3, 600, 120, 50, true));
        assertEquals(0, r.get(3L).estimatedWaitMinutes());
        Map<Long, EtaResult> one = estimator.estimateAll(ctx(3, 0, 1, 600, 120, 50, true));
        assertTrue(one.get(3L).estimatedWaitMinutes() > r.get(3L).estimatedWaitMinutes());
    }

    @Test
    void positionsAreOrderedAndRangesBracketThePoint() {
        Map<Long, EtaResult> r = estimator.estimateAll(ctx(4, 1, 2, 300, 90, 40, true));
        int prev = -1;
        for (long id = 1; id <= 4; id++) {
            EtaResult e = r.get(id);
            assertTrue(e.estimatedWaitMinutes() >= prev);
            assertTrue(e.waitMinutesMin() <= e.estimatedWaitMinutes());
            assertTrue(e.waitMinutesMax() >= e.estimatedWaitMinutes());
            assertFalse(e.turnTimeMin().isAfter(e.estimatedTurnTime()));
            assertFalse(e.turnTimeMax().isBefore(e.estimatedTurnTime()));
            prev = e.estimatedWaitMinutes();
        }
    }

    @Test
    void closedBusinessGivesNoNumericEstimate() {
        EtaResult r = estimator.estimateForNewJoin(ctx(2, 0, 1, 600, 120, 50, false));
        assertFalse(r.businessOpen());
        assertNull(r.estimatedWaitMinutes());
        assertNotNull(r.note());
    }

    @Test
    void confidenceDependsOnSamples() {
        assertEquals("LOW", estimator.estimateForNewJoin(ctx(1, 0, 1, 600, 120, 2, true)).confidence());
        assertEquals("MEDIUM", estimator.estimateForNewJoin(ctx(1, 0, 1, 600, 120, 15, true)).confidence());
        assertEquals("HIGH", estimator.estimateForNewJoin(ctx(1, 0, 1, 600, 120, 60, true)).confidence());
    }

    @Test
    void noOpenCounterLowersConfidenceAndAddsNote() {
        EtaResult r = estimator.estimateForNewJoin(ctx(1, 0, 0, 600, 120, 60, true));
        assertEquals("LOW", r.confidence());
        assertTrue(r.note().contains("No counter"));
    }

    @Test
    void warnsWhenTurnFallsAfterClosing() {
        EtaContext c = new EtaContext(now, List.of(new EtaContext.Waiting(1L, null), new EtaContext.Waiting(2L, null)),
                List.of(), 1, 1800, 300, 50, true, now.plusSeconds(1800));
        EtaResult r = estimator.estimateAll(c).get(2L);
        assertTrue(r.note() != null && r.note().contains("closing"));
    }

    @Test
    void appointmentCannotBeServedBeforeItsSlot() {
        EtaContext c = new EtaContext(now, List.of(new EtaContext.Waiting(1L, now.plusSeconds(3600))), List.of(), 1, 600, 60, 50, true, null);
        assertTrue(estimator.estimateAll(c).get(1L).estimatedWaitMinutes() >= 60);
    }
}
