package com.smartqueue.unit;

import com.smartqueue.entity.*;
import com.smartqueue.scheduling.QueueScheduler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QueueSchedulerTest {
    private final QueueScheduler scheduler = new QueueScheduler();
    private final Instant now = Instant.parse("2026-01-05T10:00:00Z");

    private QueuePolicy policy() {
        QueuePolicy p = new QueuePolicy();
        p.setMaxWaitMinutes(0);
        p.setFairnessEveryN(0);
        p.setAgingPointsPerMinute(0);
        return p;
    }

    private QueueEntry entry(long id, PriorityLevel level, int minutesAgo) {
        QueueEntry e = new QueueEntry();
        e.setId(id);
        e.setPriority(level);
        e.setStatus(EntryStatus.WAITING);
        e.setQueueTime(now.minus(Duration.ofMinutes(minutesAgo)));
        e.setJoinedAt(e.getQueueTime());
        return e;
    }

    private QueueEntry appointmentEntry(long id, int minutesAgo, int startOffsetMinutes) {
        QueueEntry e = entry(id, PriorityLevel.APPOINTMENT, minutesAgo);
        Appointment a = new Appointment();
        a.setStartAt(now.plus(Duration.ofMinutes(startOffsetMinutes)));
        e.setAppointment(a);
        return e;
    }

    @Test
    void normalCustomersAreServedFifo() {
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 5), entry(2, PriorityLevel.NORMAL, 10), entry(3, PriorityLevel.NORMAL, 1));
        assertEquals(2L, scheduler.pickNext(w, policy(), 0, now).orElseThrow().getId());
        assertEquals(List.of(2L, 1L, 3L), scheduler.order(w, policy(), 0, now).stream().map(QueueEntry::getId).toList());
    }

    @Test
    void emergencyBeatsEverything() {
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 100), entry(2, PriorityLevel.PRIORITY, 50), entry(3, PriorityLevel.EMERGENCY, 1));
        assertEquals(3L, scheduler.pickNext(w, policy(), 0, now).orElseThrow().getId());
    }

    @Test
    void priorityBeatsNormalWithoutAging() {
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 30), entry(2, PriorityLevel.PRIORITY, 1));
        assertEquals(2L, scheduler.pickNext(w, policy(), 0, now).orElseThrow().getId());
    }

    @Test
    void agingLetsLongWaitingNormalOvertakePriority() {
        QueuePolicy p = policy();
        p.setAgingPointsPerMinute(5.0);   // 100 min * 5 = 500 > priority weight 200
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 100), entry(2, PriorityLevel.PRIORITY, 1));
        assertEquals(1L, scheduler.pickNext(w, p, 0, now).orElseThrow().getId());
    }

    @Test
    void starvationGuardServesLongWaiterBeforeHigherPriority() {
        QueuePolicy p = policy();
        p.setMaxWaitMinutes(60);
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 61), entry(2, PriorityLevel.PRIORITY, 1));
        assertEquals(1L, scheduler.pickNext(w, p, 0, now).orElseThrow().getId());
    }

    @Test
    void emergencyStillWinsOverStarvationGuard() {
        QueuePolicy p = policy();
        p.setMaxWaitMinutes(60);
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 120), entry(2, PriorityLevel.EMERGENCY, 1));
        assertEquals(2L, scheduler.pickNext(w, p, 0, now).orElseThrow().getId());
    }

    @Test
    void fairnessServesNormalAfterNConsecutivePriorityCalls() {
        QueuePolicy p = policy();
        p.setFairnessEveryN(3);
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.NORMAL, 5), entry(2, PriorityLevel.PRIORITY, 4));
        assertEquals(2L, scheduler.pickNext(w, p, 2, now).orElseThrow().getId());
        assertEquals(1L, scheduler.pickNext(w, p, 3, now).orElseThrow().getId());
    }

    @Test
    void consecutiveCounterRulesRespectEmergencyBypass() {
        QueuePolicy p = policy();
        assertEquals(0, scheduler.nextConsecutive(PriorityLevel.NORMAL, 5, p));
        assertEquals(6, scheduler.nextConsecutive(PriorityLevel.PRIORITY, 5, p));
        assertEquals(5, scheduler.nextConsecutive(PriorityLevel.EMERGENCY, 5, p));
        p.setEmergencyBypassesFairness(false);
        assertEquals(6, scheduler.nextConsecutive(PriorityLevel.EMERGENCY, 5, p));
    }

    @Test
    void appointmentInsideWindowOutranksPriority() {
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.PRIORITY, 20), appointmentEntry(2, 1, 5));
        assertEquals(2L, scheduler.pickNext(w, policy(), 0, now).orElseThrow().getId());
    }

    @Test
    void tooEarlyAppointmentBehavesLikeNormal() {
        QueueEntry early = appointmentEntry(2, 1, 120);   // starts in 2h, window is 15 min
        assertEquals(PriorityLevel.NORMAL, scheduler.effectiveLevel(early, policy(), now));
        List<QueueEntry> w = List.of(entry(1, PriorityLevel.PRIORITY, 20), early);
        assertEquals(1L, scheduler.pickNext(w, policy(), 0, now).orElseThrow().getId());
    }

    @Test
    void lateAppointmentLosesItsPriority() {
        QueueEntry late = appointmentEntry(2, 30, -60);   // started 60 min ago, grace is 10
        assertEquals(PriorityLevel.NORMAL, scheduler.effectiveLevel(late, policy(), now));
    }

    @Test
    void fifoAppointmentPolicyIgnoresAppointments() {
        QueuePolicy p = policy();
        p.setAppointmentPolicy(AppointmentPolicy.FIFO);
        assertEquals(PriorityLevel.NORMAL, scheduler.effectiveLevel(appointmentEntry(1, 1, 0), p, now));
    }

    @Test
    void orderMatchesRepeatedPickNextAndIncludesEveryone() {
        QueuePolicy p = policy();
        p.setFairnessEveryN(2);
        List<QueueEntry> w = new ArrayList<>();
        for (long i = 1; i <= 6; i++) w.add(entry(i, i % 2 == 0 ? PriorityLevel.PRIORITY : PriorityLevel.NORMAL, (int) (20 - i)));
        List<QueueEntry> ordered = scheduler.order(w, p, 0, now);
        assertEquals(6, ordered.size());
        assertEquals(6, ordered.stream().map(QueueEntry::getId).distinct().count());
        assertEquals(scheduler.pickNext(w, p, 0, now).orElseThrow().getId(), ordered.get(0).getId());
    }

    @Test
    void emptyQueueYieldsNothing() {
        assertTrue(scheduler.pickNext(List.of(), policy(), 0, now).isEmpty());
    }
}
